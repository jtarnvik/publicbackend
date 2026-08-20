#!/usr/bin/env python3
"""
Is a train's reported position as fresh as the timestamp attached to it?

GtfsPredictionUtil derives delay as `timestamp - scheduledTimeAt(position)`. The error in that
number is therefore exactly the gap between when the position was measured and the timestamp
stamped on it. This polls the Samtrafiken VehiclePositions feed for a few minutes and classifies
every consecutive pair of samples per vehicle:

  MOVED       position changed          — the report is live
  STAMP-ONLY  timestamp moved, position did not  — the smoking gun: delay ramps up until the
                                                   vehicle reaches the next station
  FROZEN      neither moved             — honest staleness; the delay stays correct, only the
                                          triangle on the schematic lags

This is what established that the train feed differs from the bus feed, and so why
GtfsPositionAnchors exists. Re-run it before revisiting any of that reasoning: it rests entirely on
how the upstream feed behaves, which is not ours to control and may change.

No dependencies — the protobuf is decoded by field number below rather than through
gtfs-realtime-bindings, so the script runs against a bare Python 3.

The realtime key comes from SAMTRAFIKEN_REALTIME_API_KEY, or failing that from the gitignored
application-local.properties. It is never printed, and is scrubbed from any error message.

  python3 scripts/rt_freshness.py [--polls 16] [--interval 20]

Attributing a vehicle to a line needs the static feed the pipeline unzips into
/tmp/sl-gtfs-cache/unzipped. Without it the script still runs, but every vehicle is unattributed —
point --gtfs at an unzipped extract if the default is missing or stale.
"""

import argparse
import collections
import gzip
import math
import os
import re
import struct
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
PROPS = REPO_ROOT / "src/main/resources/application-local.properties"
GTFS = "/tmp/sl-gtfs-cache/unzipped"
FEED_URL = "https://opendata.samtrafiken.se/gtfs-rt/sl/VehiclePositions.pb"

# The lines gtfs_monitored_route tracks, by route_short_name.
MONITORED = {"43": "TRAIN", "44": "TRAIN", "112": "BUS", "117": "BUS",
             "17": "METRO", "18": "METRO", "19": "METRO"}

# GTFS extended route types per mode, mirroring the TransportMode enum. Verified against the feed on
# 2026-08-20: 100 rail, 700 bus, 401 metro.
MODE_ROUTE_TYPE = {"TRAIN": 100, "BUS": 700, "METRO": 401}


# ----------------------------------------------------------------------------- protobuf decoding

def read_varint(buf, i):
    result = 0
    shift = 0
    while True:
        b = buf[i]
        i += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, i
        shift += 7


def decode(buf):
    """Protobuf wire format into {field_number: [values]}. Length-delimited stays as raw bytes."""
    fields = collections.defaultdict(list)
    i = 0
    end = len(buf)
    while i < end:
        key, i = read_varint(buf, i)
        field, wire = key >> 3, key & 7
        if wire == 0:
            value, i = read_varint(buf, i)
        elif wire == 1:
            value, i = buf[i:i + 8], i + 8
        elif wire == 2:
            length, i = read_varint(buf, i)
            value, i = buf[i:i + length], i + length
        elif wire == 5:
            value, i = struct.unpack("<f", buf[i:i + 4])[0], i + 4
        else:
            raise ValueError(f"unsupported wire type {wire}")
        fields[field].append(value)
    return fields


def one(fields, number, default=None):
    values = fields.get(number)
    return values[0] if values else default


def parse_feed(raw):
    """FeedMessage: header=1, entity=2. Returns (header_timestamp, [vehicle dicts])."""
    feed = decode(raw)
    header = decode(one(feed, 1, b""))
    header_ts = one(header, 3, 0)

    vehicles = []
    for entity_bytes in feed.get(2, []):
        entity = decode(entity_bytes)
        vp_bytes = one(entity, 4)              # FeedEntity.vehicle = 4
        if vp_bytes is None:
            continue
        vp = decode(vp_bytes)
        trip = decode(one(vp, 1, b""))         # VehiclePosition.trip = 1
        pos = decode(one(vp, 2, b""))          # VehiclePosition.position = 2
        desc = decode(one(vp, 8, b""))         # VehiclePosition.vehicle = 8
        vehicles.append({
            "trip_id": one(trip, 1, b"").decode(),      # TripDescriptor.trip_id = 1
            "lat": one(pos, 1, 0.0),                    # Position.latitude = 1
            "lng": one(pos, 2, 0.0),                    # Position.longitude = 2
            "bearing": one(pos, 3, 0.0),                # Position.bearing = 3
            "speed": one(pos, 5, 0.0),                  # Position.speed = 5
            "timestamp": one(vp, 5, 0),                 # VehiclePosition.timestamp = 5
            "vehicle_id": one(desc, 1, b"").decode(),   # VehicleDescriptor.id = 1
        })
    return header_ts, vehicles


# ----------------------------------------------------------------------------- feed access

def read_key(props):
    """The realtime key, from the environment or the gitignored local properties. Never printed."""
    from_env = os.environ.get("SAMTRAFIKEN_REALTIME_API_KEY")
    if from_env:
        return from_env.strip()
    try:
        with open(props) as f:
            for line in f:
                if line.strip().startswith("samtrafiken.gtfs-realtime-api-key"):
                    return line.split("=", 1)[1].strip()
    except OSError as e:
        sys.exit(f"Set SAMTRAFIKEN_REALTIME_API_KEY, or make {props} readable: {e}")
    sys.exit(f"No samtrafiken.gtfs-realtime-api-key in {props}, and "
             "SAMTRAFIKEN_REALTIME_API_KEY is not set")


def scrub(text, key):
    """Nothing derived from an error message may carry the key into the output."""
    return re.sub(re.escape(key), "<key>", str(text)) if key else str(text)


def fetch(key):
    request = urllib.request.Request(f"{FEED_URL}?key={key}",
                                     headers={"Accept-Encoding": "gzip"})
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read()
        if response.headers.get("Content-Encoding") == "gzip":
            raw = gzip.decompress(raw)
    return parse_feed(raw)


# ----------------------------------------------------------------------------- static join

def base_line_name(designation):
    """
    A designation without its variant suffix — one optional trailing letter.

    The same rule as `GtfsNameUtil.baseLineName`. Matching on the exact name instead looks harmless
    and is not: line 43 also runs as 43A, 43B, 43D, 43M, 43T and 43X, and an exact filter drops every
    one of them while still reporting a healthy-looking count of plain 43s. The figures quoted in
    CLAUDE.md were measured before this was corrected (2026-08-20) and so cover plain 43/44 only.
    """
    return re.sub(r"[A-Za-z]$", "", designation.strip())


def is_monitored(short_name, route_type):
    """
    Whether this route is one of ours — by name *and* by mode, which is not optional.

    Line numbers are not unique across modes in this feed. 17 and 19 are green line metro routes and
    also Waxholmsbolaget ferry routes (route_type 1000); 43A, 43B, 43D, 43M, 43T and 43X exist as
    "Ersätter Pendeltåget" replacement buses (route_type 700) alongside the real line 43 train. A
    name-only filter swallows all of them, which is how this script briefly disagreed with the
    database by 219 trips. GtfsParseService.matchesMonitoredRoute checks both; so does this.
    """
    mode = MONITORED.get(base_line_name(short_name))
    if mode is None:
        return False
    try:
        return int(route_type) == MODE_ROUTE_TYPE[mode]
    except (TypeError, ValueError):
        return False


def load_monitored_trips(gtfs):
    """trip_id -> (line, mode) for the monitored lines, from the unzipped static extract."""
    import csv
    routes = {}
    try:
        with open(f"{gtfs}/routes.txt", newline="") as f:
            for row in csv.DictReader(f):
                if is_monitored(row["route_short_name"], row["route_type"]):
                    routes[row["route_id"]] = row["route_short_name"]
    except OSError:
        print(f"No static extract at {gtfs} — vehicles cannot be attributed to a line.\n")
        return {}
    trips = {}
    with open(f"{gtfs}/trips.txt", newline="") as f:
        for row in csv.DictReader(f):
            line = routes.get(row["route_id"])
            if line:
                trips[row["trip_id"]] = (line, MONITORED[base_line_name(line)])
    return trips


AT_STOP_METRES = 150.0


def load_trip_stops(trip_ids, gtfs):
    """{trip_id: [(lat, lng), ...]} for the observed trips, from one pass over stop_times.txt."""
    import csv
    import subprocess
    import tempfile

    coords = {}
    try:
        with open(f"{gtfs}/stops.txt", newline="") as f:
            for row in csv.DictReader(f):
                coords[row["stop_id"]] = (float(row["stop_lat"]), float(row["stop_lon"]))
    except OSError:
        return {}

    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as patterns:
        patterns.write("\n".join(trip_ids) + "\n")
        pattern_path = patterns.name
    try:
        # stop_times.txt is ~140 MB; grep it once for every trip rather than per trip.
        result = subprocess.run(["grep", "-F", "-f", pattern_path, f"{gtfs}/stop_times.txt"],
                                capture_output=True, text=True, check=False)
    except OSError:
        return {}

    wanted = set(trip_ids)
    stops_by_trip = collections.defaultdict(list)
    for line in result.stdout.splitlines():
        parts = line.split(",")
        if len(parts) > 3 and parts[0] in wanted and parts[3] in coords:
            stops_by_trip[parts[0]].append(coords[parts[3]])
    return stops_by_trip


def hav(lat1, lng1, lat2, lng2):
    R = 6371000.0
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    h = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng / 2) ** 2)
    return 2 * R * math.asin(math.sqrt(h))


# ----------------------------------------------------------------------------- reporting

def clock(epoch):
    return datetime.fromtimestamp(epoch).strftime("%H:%M:%S") if epoch else "--:--:--"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--polls", type=int, default=16)
    parser.add_argument("--interval", type=int, default=20,
                        help="seconds between polls; polls*interval should exceed one train "
                             "segment (line 43 segments run 3-6 minutes)")
    parser.add_argument("--gtfs", default=GTFS,
                        help="unzipped static GTFS extract, for attributing vehicles to a line")
    parser.add_argument("--props", default=str(PROPS),
                        help="properties file to read the realtime key from")
    args = parser.parse_args()

    key = read_key(args.props)
    trips = load_monitored_trips(args.gtfs)
    print(f"{len(trips)} monitored trip ids in the static extract "
          f"(from {args.gtfs}, which may be older than the live feed)\n")

    # samples[(trip_id)] = list of (wall, lat, lng, ts, speed)
    samples = collections.defaultdict(list)
    meta = {}
    joined_counts = []

    for poll in range(args.polls):
        if poll:
            time.sleep(args.interval)
        wall = time.time()
        try:
            header_ts, vehicles = fetch(key)
        except (urllib.error.URLError, OSError, ValueError) as e:
            print(f"poll {poll}: fetch failed: {scrub(e, key)}")
            continue

        joined = 0
        stamps = []
        for v in vehicles:
            info = trips.get(v["trip_id"])
            if not info:
                continue
            joined += 1
            stamps.append(v["timestamp"])
            samples[v["trip_id"]].append((wall, v["lat"], v["lng"], v["timestamp"], v["speed"]))
            meta[v["trip_id"]] = (info[0], info[1], v["vehicle_id"])
        joined_counts.append(joined)

        ages = sorted(wall - s for s in stamps if s)
        median_age = ages[len(ages) // 2] if ages else float("nan")
        same_as_header = sum(1 for s in stamps if s == header_ts)
        print(f"poll {poll:2d} at {clock(wall)}  header={clock(header_ts)} "
              f"(header age {wall - header_ts:5.1f}s)  {len(vehicles):4d} vehicles, "
              f"{joined:3d} monitored  median position age {median_age:5.1f}s  "
              f"{same_as_header}/{len(stamps)} stamped exactly as the header")

    if not samples:
        sys.exit("\nNo monitored vehicles seen. If the static extract is stale the trip ids will "
                 "not join — re-run when the backend has parsed a current feed.")

    # ------------------------------------------------------------------ transitions per mode
    print("\n" + "=" * 78)
    print("Consecutive-sample transitions, by mode")
    print("=" * 78)

    kinds = collections.defaultdict(collections.Counter)
    longest_stamp_only = collections.defaultdict(float)
    age_by_mode = collections.defaultdict(list)

    for trip_id, series in samples.items():
        line, mode, _ = meta[trip_id]
        run = 0.0
        for (w0, la0, ln0, ts0, _), (w1, la1, ln1, ts1, _) in zip(series, series[1:]):
            moved = hav(la0, ln0, la1, ln1)
            if moved > 1.0:
                kind = "MOVED"
                run = 0.0
            elif ts1 > ts0:
                kind = "STAMP-ONLY"
                run += w1 - w0
                longest_stamp_only[mode] = max(longest_stamp_only[mode], run)
            else:
                kind = "FROZEN"
                run = 0.0
            kinds[mode][kind] += 1
        for w, _, _, ts, _ in series:
            if ts:
                age_by_mode[mode].append(w - ts)

    print(f"{'mode':<8}{'vehicles':>9}{'MOVED':>9}{'STAMP-ONLY':>12}{'FROZEN':>9}"
          f"{'median age':>12}{'longest stamp-only run':>25}")
    for mode in sorted(kinds):
        counter = kinds[mode]
        total = sum(counter.values()) or 1
        vehicles = sum(1 for t in samples if meta[t][1] == mode)
        ages = sorted(age_by_mode[mode])
        median_age = ages[len(ages) // 2] if ages else float("nan")
        print(f"{mode:<8}{vehicles:>9}"
              f"{counter['MOVED'] * 100 // total:>8}%"
              f"{counter['STAMP-ONLY'] * 100 // total:>11}%"
              f"{counter['FROZEN'] * 100 // total:>8}%"
              f"{median_age:>11.0f}s"
              f"{longest_stamp_only[mode]:>24.0f}s")

    # ------------------------------------------------------------------ phantom delay
    # estimateDelay computes `timestamp - scheduledTimeAt(position)`. When the timestamp advances
    # while the position does not, the whole age of that position is charged to the traffic. Taking
    # the earliest timestamp seen at a position as the moment the vehicle was really there, the
    # inflation is timestamp - that earliest timestamp: minutes of delay that are not happening.
    print("\n" + "=" * 78)
    print("Phantom delay — how much of the reported delay is just a stale position")
    print("=" * 78)

    # A vehicle standing at a stop legitimately reports the same position with an advancing
    # timestamp, and the delay computed then is correct — so samples near one of the trip's own
    # stops are excluded rather than counted as phantom.
    trip_stops = load_trip_stops(list(samples), args.gtfs)
    print(f"(stop chains resolved for {len(trip_stops)} of {len(samples)} observed trips; "
          f"samples within {AT_STOP_METRES:.0f} m of one of the trip's own stops are treated as "
          f"dwelling, not stale)\n")

    phantom_by_mode = collections.defaultdict(list)
    at_stop_by_mode = collections.Counter()
    worst_examples = []
    for trip_id, series in samples.items():
        line, mode, _ = meta[trip_id]
        own = trip_stops.get(trip_id)
        anchor_ts = None
        anchor_pos = None
        for wall, lat, lng, ts, _ in series:
            if anchor_pos is None or hav(anchor_pos[0], anchor_pos[1], lat, lng) > 1.0:
                anchor_pos, anchor_ts = (lat, lng), ts
            phantom = ts - anchor_ts if ts and anchor_ts else 0
            if own and min(hav(lat, lng, s[0], s[1]) for s in own) < AT_STOP_METRES:
                at_stop_by_mode[mode] += 1
                continue
            phantom_by_mode[mode].append(phantom)
            worst_examples.append((phantom, mode, line, trip_id, clock(wall)))

    print(f"{'mode':<8}{'samples':>9}{'at a stop':>11}{'median':>9}{'p90':>8}{'max':>8}"
          f"{'  inflated by >60s':>20}")
    for mode in sorted(phantom_by_mode):
        values = sorted(phantom_by_mode[mode])
        n = len(values) or 1
        over_minute = sum(1 for v in values if v > 60)
        print(f"{mode:<8}{n:>9}{at_stop_by_mode[mode]:>11}{values[n // 2]:>8.0f}s"
              f"{values[min(n - 1, int(0.9 * n))]:>7.0f}s{values[-1]:>7.0f}s"
              f"{over_minute * 100 // n:>18}%")

    print("\n  worst individual samples, away from any stop of the trip:")
    for phantom, mode, line, trip_id, at in sorted(worst_examples, reverse=True)[:6]:
        print(f"    {mode} {line} trip {trip_id} at {at}: "
              f"reported delay inflated by {phantom / 60:.1f} min")

    # ------------------------------------------------------------------ sample timelines
    print("\n" + "=" * 78)
    print("Timelines — the longest-observed vehicle per mode")
    print("=" * 78)

    by_mode = collections.defaultdict(list)
    for trip_id, series in samples.items():
        by_mode[meta[trip_id][1]].append((len(series), trip_id))

    for mode in sorted(by_mode):
        for _, trip_id in sorted(by_mode[mode], reverse=True)[:2]:
            line, _, vehicle_id = meta[trip_id]
            series = samples[trip_id]
            print(f"\n{mode} {line}  trip {trip_id}  vehicle {vehicle_id or '-'}")
            print(f"  {'wall':>9}{'stamp':>10}{'age':>7}{'moved':>9}{'speed':>8}   ")
            previous = None
            for wall, lat, lng, ts, speed in series:
                moved = "-" if previous is None else f"{hav(previous[0], previous[1], lat, lng):.0f} m"
                print(f"  {clock(wall):>9}{clock(ts):>10}{wall - ts:>6.0f}s{moved:>9}"
                      f"{speed:>7.1f}   {lat:.5f},{lng:.5f}")
                previous = (lat, lng)

    # ------------------------------------------------------------------ verdict
    print("\n" + "=" * 78)
    print("Reading it")
    print("=" * 78)
    print("""
  MOVED dominant, ages a few seconds
      Positions are live. The 4-minute error is not feed lag — look elsewhere.

  STAMP-ONLY dominant for TRAIN, with runs approaching a segment (line 43: 3-6 min)
      Confirmed. The position freezes between stations while the timestamp advances, so
      estimateDelay ramps its answer up across the segment and it collapses on arrival.
      Fix: hold the delay computed at the last station instead of recomputing it from a
      position that has not moved.

  FROZEN dominant, ages growing into minutes
      Honest staleness: the timestamp is the fix time, so the delay stays correct and only
      the drawn triangle lags. The fix is then to extrapolate the position, not the delay —
      the opposite change.

  Also worth reading: 'stamped exactly as the header' per poll. If train stamps equal the
  header timestamp, the feed is stamping publish time rather than fix time, and any upstream
  latency becomes phantom delay 1:1.
""")


if __name__ == "__main__":
    main()
