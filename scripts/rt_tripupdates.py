#!/usr/bin/env python3
"""
Does SL publish a TripUpdates feed on our realtime key, and does it tell us anything
VehiclePositions cannot?

Everything the live traffic view knows about lateness is reconstructed from geometry: the vehicle's
position is projected onto its own trip, the scheduled time at that point is interpolated, and the
gap is called delay. That exists because this project has always held that "there is no TripUpdates
feed" — a claim about what the backend *fetches*, never once tested against the API. Trafiklab
publishes TripUpdates.pb as a sibling path to the VehiclePositions.pb we already poll, on the same
key. If SL populates it, it carries observed delay per trip_id — the key we already join on — and a
whole class of approximation disappears.

Three questions, in order of how much they decide:

  1. Does the URL answer on our key at all, and is the feed populated for the monitored lines?
  2. What is actually in it? The per-field availability table, in the same shape as the
     VehiclePositions one in CLAUDE.md, because that table is what gets pasted there afterwards.
  3. THE DECISIVE ONE: does a trip update advance while the vehicle's *position* is frozen?

Question 3 is the point of the whole script. Train positions in VehiclePositions are quantised by
track section — long jumps between stations, shorter and more frequent ones on the approach — so a
train's reported position is routinely a minute or more behind the truth. That is tolerable for the
delay we compute (GtfsPositionAnchors freezes it rather than inflating it) but it is the source of
the sawtooth: a delay interpolated from a stale position runs pessimistic just after a departure and
corrects into the next station. A TripUpdates feed that only refreshes when the position does would
inherit exactly the same blind spot and would not be worth adopting. One that moves independently is
a genuinely new observation, and replaces the geometry outright.

So both feeds are polled together and every consecutive pair is cross-tabulated: did the position
move, and did the trip update change? The cell to read is FROZEN x ADVANCED.

No dependencies — the protobuf is decoded by field number below rather than through
gtfs-realtime-bindings, so the script runs against a bare Python 3.

The realtime key comes from SAMTRAFIKEN_REALTIME_API_KEY, or failing that from the gitignored
application-local.properties. It is never printed, and is scrubbed from any error message.

  python3 scripts/rt_tripupdates.py [--polls 16] [--interval 20]
  python3 scripts/rt_tripupdates.py --probe-only     # question 1 alone, one request, no waiting

Attributing a trip to a line takes the development database, which is where the pipeline has already
put exactly this: gtfs_trip is filtered to the monitored routes as it is parsed, and
gtfs_monitored_route carries the mode. Reading it there rather than from the static files is not only
cheaper — the static zip is Bronze tier at 50 calls a month, so it is never downloaded from here —
but more honest, because a trip the query cannot attribute is precisely a trip the backend cannot
attribute either. The connection details come from the same properties file as the realtime key.

Failing that (no client, no route to the database, or a schema the pipeline has never filled) it
falls back to the extract the pipeline unzips into /tmp/sl-gtfs-cache/unzipped, and failing that it
runs anyway with the per-mode tables empty. --static-source forces one or the other.
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
BASE_URL = "https://opendata.samtrafiken.se/gtfs-rt/sl"
TRIP_UPDATES_URL = f"{BASE_URL}/TripUpdates.pb"
VEHICLE_POSITIONS_URL = f"{BASE_URL}/VehiclePositions.pb"

# The lines gtfs_monitored_route tracks, by route_short_name.
MONITORED = {"43": "TRAIN", "44": "TRAIN", "112": "BUS", "117": "BUS",
             "17": "METRO", "18": "METRO", "19": "METRO"}

# GTFS extended route types per mode, mirroring the TransportMode enum. Verified against the feed on
# 2026-08-20: 100 rail, 700 bus, 401 metro.
MODE_ROUTE_TYPE = {"TRAIN": 100, "BUS": 700, "METRO": 401}

# A position has to differ by more than this to count as having moved. Live GPS never repeats a
# reading exactly, so anything above float noise separates "the feed re-sent a value" from "the
# vehicle is somewhere else" — the same rule GtfsPositionAnchors applies.
MOVED_METRES = 1.0

SCHEDULE_RELATIONSHIP = {0: "SCHEDULED", 1: "ADDED", 2: "UNSCHEDULED", 3: "CANCELED",
                         5: "DUPLICATED", 6: "DELETED"}


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


def signed(value):
    """
    A varint back into the int32 it was.

    Delay is the one signed field that matters here, and an early vehicle is the case that breaks a
    naive reader: protobuf encodes a negative int32 sign-extended to 64 bits, so -60 arrives as a
    ten-byte varint reading 18446744073709551556. Without this, every early vehicle would show up as
    delayed by roughly 584 billion years, which is at least easy to spot.
    """
    if value is None:
        return None
    return value - (1 << 64) if value >= (1 << 63) else value


def parse_trip_updates(raw):
    """FeedMessage -> (header_timestamp, [trip update dicts]). Field numbers from gtfs-realtime.proto."""
    feed = decode(raw)
    header = decode(one(feed, 1, b""))
    header_ts = one(header, 3, 0)

    updates = []
    for entity_bytes in feed.get(2, []):
        entity = decode(entity_bytes)
        tu_bytes = one(entity, 3)                    # FeedEntity.trip_update = 3
        if tu_bytes is None:
            continue
        tu = decode(tu_bytes)
        trip = decode(one(tu, 1, b""))               # TripUpdate.trip = 1
        desc = decode(one(tu, 3, b""))               # TripUpdate.vehicle = 3

        stop_updates = []
        for stu_bytes in tu.get(2, []):              # TripUpdate.stop_time_update = 2
            stu = decode(stu_bytes)
            arrival = decode(one(stu, 2, b""))       # StopTimeUpdate.arrival = 2
            departure = decode(one(stu, 3, b""))     # StopTimeUpdate.departure = 3
            stop_updates.append({
                "stop_sequence": one(stu, 1),                    # StopTimeUpdate.stop_sequence = 1
                "stop_id": one(stu, 4, b"").decode(),            # StopTimeUpdate.stop_id = 4
                "schedule_relationship": one(stu, 5),            # StopTimeUpdate.schedule_relationship = 5
                "arrival_delay": signed(one(arrival, 1)),        # StopTimeEvent.delay = 1
                "arrival_time": one(arrival, 2),                 # StopTimeEvent.time = 2
                "arrival_uncertainty": signed(one(arrival, 3)),  # StopTimeEvent.uncertainty = 3
                "departure_delay": signed(one(departure, 1)),
                "departure_time": one(departure, 2),
                "departure_uncertainty": signed(one(departure, 3)),
            })

        updates.append({
            "trip_id": one(trip, 1, b"").decode(),               # TripDescriptor.trip_id = 1
            "route_id": one(trip, 5, b"").decode(),              # TripDescriptor.route_id = 5
            "start_date": one(trip, 3, b"").decode(),            # TripDescriptor.start_date = 3
            "schedule_relationship": one(trip, 4),               # TripDescriptor.schedule_relationship = 4
            "timestamp": one(tu, 4, 0),                          # TripUpdate.timestamp = 4
            "delay": signed(one(tu, 5)),                         # TripUpdate.delay = 5
            "vehicle_id": one(desc, 1, b"").decode(),            # VehicleDescriptor.id = 1
            "stop_updates": stop_updates,
        })
    return header_ts, updates


def parse_vehicle_positions(raw):
    """The subset of VehiclePositions this script needs — position, stamp, and the joining trip id."""
    feed = decode(raw)
    vehicles = []
    for entity_bytes in feed.get(2, []):
        entity = decode(entity_bytes)
        vp_bytes = one(entity, 4)                    # FeedEntity.vehicle = 4
        if vp_bytes is None:
            continue
        vp = decode(vp_bytes)
        trip = decode(one(vp, 1, b""))               # VehiclePosition.trip = 1
        pos = decode(one(vp, 2, b""))                # VehiclePosition.position = 2
        vehicles.append({
            "trip_id": one(trip, 1, b"").decode(),
            "lat": one(pos, 1, 0.0),
            "lng": one(pos, 2, 0.0),
            "timestamp": one(vp, 5, 0),              # VehiclePosition.timestamp = 5
        })
    return vehicles


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


def fetch_raw(url, key):
    """
    The bytes of one feed.

    Accept-Encoding: gzip is not optional — the API answers 406 without it, which is the single most
    likely way for this probe to produce a false "no such feed".
    """
    request = urllib.request.Request(f"{url}?key={key}", headers={"Accept-Encoding": "gzip"})
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read()
        if response.headers.get("Content-Encoding") == "gzip":
            raw = gzip.decompress(raw)
        return response.status, raw


def explain_http_error(e, key):
    """
    What a failing status means for the decision, rather than what it means in general.

    The whole first question is whether this feed exists on our key, so a status code here is the
    answer rather than an error to retry past.
    """
    hints = {
        401: "the realtime key was rejected — check it is the realtime key, not the static one",
        403: "the key is valid but not entitled to TripUpdates. This is the answer to question 1: "
             "the feed exists, our subscription does not cover it. Trafiklab's console shows which "
             "feeds a key carries, and TripUpdates may need its own.",
        404: "no such feed for SL. This confirms what CLAUDE.md has been claiming, and the "
             "geometry-derived delay stays the only option.",
        406: "the API refused the encoding, which should be impossible here — this script always "
             "sends Accept-Encoding: gzip. Check for a proxy stripping the header.",
        429: "quota exhausted for the rolling window. Not an answer either way; try again later.",
    }
    hint = hints.get(e.code, "unexpected status — treat as inconclusive rather than as a no")
    return f"HTTP {e.code} {scrub(e.reason, key)}\n  {hint}"


# ----------------------------------------------------------------------------- monitored trips

# The pipeline has already done this join once, and gtfs_trip only ever holds monitored routes, so
# the database answers in one query what the static files take two passes and 6 MB to say. The
# regexp mirrors GtfsNameUtil.baseLineName: one optional trailing letter, so 43X still matches 43.
# It is reported under its own name rather than folded into 43, which is the one place this source
# differs from the files: knowing a vehicle is on a variant is worth seeing in a diagnostic.
MONITORED_TRIPS_SQL = """
  SELECT t.trip_id, r.route_short_name, m.transport_mode
    FROM gtfs_trip t
    JOIN gtfs_route r ON r.route_id = t.route_id
    JOIN gtfs_monitored_route m
      ON m.route_short_name = REGEXP_REPLACE(r.route_short_name, '[A-Za-z]$', '')
"""


def read_datasource(props):
    """
    Host, port, schema and credentials for the development database.

    The environment wins, using the same variable names the deployment already sets, so this works
    on the Mac Mini without a properties file at all. The password is handled exactly as the API key
    is: never printed, scrubbed out of anything that is.
    """
    values = {}
    wanted = {
        "spring.datasource.url": "url",
        "spring.datasource.username": "username",
        "spring.datasource.password": "password",
    }
    try:
        with open(props) as f:
            for line in f:
                key = line.split("=", 1)[0].strip()
                if key in wanted and "=" in line:
                    values[wanted[key]] = line.split("=", 1)[1].strip()
    except OSError:
        pass
    for env, name in (("DB_URL", "url"), ("DB_USERNAME", "username"), ("DB_PASSWORD", "password")):
        if os.environ.get(env):
            values[name] = os.environ[env].strip()
    if not all(k in values for k in ("url", "username", "password")):
        return None
    parsed = parse_jdbc_url(values["url"])
    if parsed is None:
        return None
    host, port, schema = parsed
    return {"host": host, "port": port, "schema": schema,
            "user": values["username"], "password": values["password"]}


def parse_jdbc_url(url):
    """`jdbc:mysql://host:port/schema?params` into its three useful parts, or None if it is not one."""
    match = re.match(r"^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?;]+)", url)
    if not match:
        return None
    return match.group(1), match.group(2) or "3306", match.group(3)


def load_monitored_trips_from_db(datasource):
    """
    trip_id -> (line, mode), straight out of the tables the pipeline fills.

    Shelling out to the mysql client rather than importing a driver keeps the no-dependencies rule —
    the same reason rt_freshness.py greps stop_times.txt instead of parsing it. The credentials go
    into a mode-600 defaults file rather than onto the command line or into the environment, so the
    password is never visible in `ps` and never inherited by anything this spawns.
    """
    import subprocess
    import tempfile

    if datasource is None:
        return {}, "no usable spring.datasource.* in the properties file or the environment"

    defaults = None
    try:
        with tempfile.NamedTemporaryFile("w", suffix=".cnf", delete=False) as f:
            defaults = f.name
            os.chmod(defaults, 0o600)
            f.write("[client]\n"
                    f"host={datasource['host']}\n"
                    f"port={datasource['port']}\n"
                    f"user={datasource['user']}\n"
                    f"password={datasource['password']}\n")
        result = subprocess.run(
            ["mysql", f"--defaults-extra-file={defaults}", "--batch", "--skip-column-names",
             "-D", datasource["schema"], "-e", MONITORED_TRIPS_SQL],
            capture_output=True, text=True, timeout=60, check=False)
    except FileNotFoundError:
        return {}, "no mysql client on PATH"
    except subprocess.SubprocessError as e:
        return {}, f"mysql failed: {scrub(e, datasource['password'])}"
    finally:
        if defaults:
            try:
                os.unlink(defaults)
            except OSError:
                pass

    if result.returncode != 0:
        detail = scrub(result.stderr.strip().splitlines()[-1] if result.stderr.strip() else "no output",
                       datasource["password"])
        return {}, f"query failed: {detail}"

    trips = {}
    for line in result.stdout.splitlines():
        parts = line.split("\t")
        if len(parts) == 3:
            trips[parts[0]] = (parts[1], parts[2])
    if not trips:
        return {}, f"{datasource['schema']} has no monitored trips — has the pipeline ever run here?"
    return trips, f"{datasource['schema']} on {datasource['host']}"


def base_line_name(designation):
    """
    A designation without its variant suffix — one optional trailing letter.

    The same rule as `GtfsNameUtil.baseLineName`, and as the SQL above. Matching on the exact name
    instead looks harmless and is not: line 43 alone runs as 43A, 43B, 43D, 43M, 43T and 43X, and an
    exact filter drops every one of them while still reporting a healthy-looking count of plain 43s.
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


def load_monitored_trips_from_files(gtfs):
    """trip_id -> (line, mode) for the monitored lines, from the unzipped static extract."""
    import csv
    routes = {}
    try:
        with open(f"{gtfs}/routes.txt", newline="") as f:
            for row in csv.DictReader(f):
                if is_monitored(row["route_short_name"], row["route_type"]):
                    routes[row["route_id"]] = row["route_short_name"]
    except OSError:
        return {}, f"no static extract at {gtfs}"
    trips = {}
    with open(f"{gtfs}/trips.txt", newline="") as f:
        for row in csv.DictReader(f):
            line = routes.get(row["route_id"])
            if line:
                trips[row["trip_id"]] = (line, MONITORED[base_line_name(line)])
    return trips, gtfs


def load_monitored_trips(args):
    """
    Whichever source can answer, preferring the database.

    Both failures are reported rather than swallowed: running with nothing attributed is a legitimate
    outcome — questions 1 and 2 do not need it — but silently losing the per-mode tables to a typo in
    a properties file is not.
    """
    if args.static_source in ("auto", "db"):
        trips, detail = load_monitored_trips_from_db(read_datasource(args.props))
        if trips:
            print(f"{len(trips)} monitored trip ids from {detail}")
            return trips
        print(f"Database attribution unavailable — {detail}")
        if args.static_source == "db":
            return {}

    if args.static_source in ("auto", "files"):
        trips, detail = load_monitored_trips_from_files(args.gtfs)
        if trips:
            print(f"{len(trips)} monitored trip ids from {detail} "
                  f"(a static extract, which may be older than the live feed)")
            return trips
        print(f"File attribution unavailable — {detail}")

    print("Nothing can be attributed to a line; the per-mode tables will be empty.")
    return {}


def hav(lat1, lng1, lat2, lng2):
    R = 6371000.0
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    h = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng / 2) ** 2)
    return 2 * R * math.asin(math.sqrt(h))


# ----------------------------------------------------------------------------- shape of an update

def observed_delay(update, now=None):
    """
    How late this vehicle was the last time SL saw it at a platform.

    **The feed only reports backwards.** Measured 2026-08-20: every stop_time_update for a stop the
    vehicle has already passed carries a real, second-precision delay, and every stop still ahead of
    it carries `delay = 0` with a `time` on an exact whole minute — the raw timetable. SL publishes
    observations, not predictions. So the current delay is the one attached to the *last passed*
    stop, and taking `stop_updates[0]` instead — which is what this function did on first writing —
    reports how late the vehicle was leaving its origin, an hour ago and no longer true.

    Stops ahead are deliberately never consulted: their zero is the absence of a measurement, and
    averaging it in would dilute every real delay towards a punctual network that does not exist.

    TripUpdate.delay would be the trip-level figure, but SL populates it 0% of the time.
    """
    if update["delay"] is not None:
        return update["delay"], "trip"
    now = time.time() if now is None else now
    best = None
    for stu in update["stop_updates"]:
        when = stu["departure_time"] or stu["arrival_time"]
        if when is None or when > now:
            continue
        delay = stu["departure_delay"] if stu["departure_delay"] is not None else stu["arrival_delay"]
        if delay is not None and (best is None or when > best[0]):
            best = (when, delay)
    if best is not None:
        return best[1], "last passed stop"
    return None, "none"


def update_fingerprint(update):
    """
    What has to change for a trip update to count as carrying news.

    Deliberately more than the timestamp: a feed that restamps an unchanged prediction is doing
    exactly what the VehiclePositions feed does with position, and counting that as movement would
    reproduce the mistake this script exists to avoid. The predicted times for the next few stops are
    the payload, so they are what is compared.
    """
    head = update["stop_updates"][:3]
    return (
        update["delay"],
        tuple((stu["stop_sequence"], stu["stop_id"], stu["arrival_time"], stu["departure_time"],
               stu["arrival_delay"], stu["departure_delay"]) for stu in head),
    )


# ----------------------------------------------------------------------------- reporting

def clock(epoch):
    return datetime.fromtimestamp(epoch).strftime("%H:%M:%S") if epoch else "--:--:--"


def percent(count, total):
    return f"{count * 100 // total}%" if total else "-"


def probe(key):
    """Question 1. Returns (header_timestamp, updates) or exits with the reason it could not."""
    print("=" * 78)
    print("1. Does the feed answer on our key?")
    print("=" * 78)
    print(f"  GET {TRIP_UPDATES_URL}?key=<key>")
    try:
        status, raw = fetch_raw(TRIP_UPDATES_URL, key)
    except urllib.error.HTTPError as e:
        sys.exit(f"  {explain_http_error(e, key)}")
    except (urllib.error.URLError, OSError) as e:
        sys.exit(f"  request failed before any status: {scrub(e, key)}")

    try:
        header_ts, updates = parse_trip_updates(raw)
    except (ValueError, IndexError, UnicodeDecodeError) as e:
        sys.exit(f"  HTTP {status}, {len(raw)} bytes, but this is not a GTFS-RT FeedMessage: "
                 f"{scrub(e, key)}\n  An HTML error page decodes like this. Inconclusive.")

    with_stops = sum(1 for u in updates if u["stop_updates"])
    print(f"  HTTP {status}, {len(raw)} bytes decoded, {len(updates)} trip updates "
          f"({with_stops} carrying stop_time_updates)")
    print(f"  feed header stamped {clock(header_ts)} (age {time.time() - header_ts:.0f}s)")
    if not updates:
        sys.exit("\n  The feed exists and is empty. Nothing to adopt — treat as a no, but re-run at "
                 "a busier hour before writing it down.")
    return header_ts, updates


def report_field_availability(updates, label):
    """
    Question 2, in the shape of the availability table in the backend CLAUDE.md.

    That table is the durable output of this script, so it is printed ready to paste rather than as
    raw counts to be interpreted afterwards.
    """
    print("\n" + "=" * 78)
    print(f"2. What is in it? ({label})")
    print("=" * 78)

    total = len(updates)
    stus = [stu for u in updates for stu in u["stop_updates"]]

    trip_fields = [
        ("trip.trip_id", sum(1 for u in updates if u["trip_id"])),
        ("trip.route_id", sum(1 for u in updates if u["route_id"])),
        ("trip.start_date", sum(1 for u in updates if u["start_date"])),
        ("trip.schedule_relationship", sum(1 for u in updates if u["schedule_relationship"] is not None)),
        ("TripUpdate.timestamp", sum(1 for u in updates if u["timestamp"])),
        ("TripUpdate.delay", sum(1 for u in updates if u["delay"] is not None)),
        ("TripUpdate.vehicle.id", sum(1 for u in updates if u["vehicle_id"])),
        ("stop_time_update (any)", sum(1 for u in updates if u["stop_updates"])),
    ]
    print(f"  {'field':<34}{'populated':>12}{'of':>8}")
    for name, count in trip_fields:
        print(f"  {name:<34}{percent(count, total):>12}{total:>8}")

    if not stus:
        print("\n  No stop_time_updates at all — without them there are no per-stop predictions, "
              "\n  only a trip-level delay.")
        return

    stu_fields = [
        ("  .stop_sequence", sum(1 for s in stus if s["stop_sequence"] is not None)),
        ("  .stop_id", sum(1 for s in stus if s["stop_id"])),
        ("  .arrival.delay", sum(1 for s in stus if s["arrival_delay"] is not None)),
        ("  .arrival.time", sum(1 for s in stus if s["arrival_time"] is not None)),
        ("  .arrival.uncertainty", sum(1 for s in stus if s["arrival_uncertainty"] is not None)),
        ("  .departure.delay", sum(1 for s in stus if s["departure_delay"] is not None)),
        ("  .departure.time", sum(1 for s in stus if s["departure_time"] is not None)),
        ("  .schedule_relationship", sum(1 for s in stus if s["schedule_relationship"] is not None)),
    ]
    print(f"\n  {'stop_time_update field':<34}{'populated':>12}{'of':>8}")
    for name, count in stu_fields:
        print(f"  {name:<34}{percent(count, len(stus)):>12}{len(stus):>8}")

    lengths = sorted(len(u["stop_updates"]) for u in updates if u["stop_updates"])
    print(f"\n  stop_time_updates per trip: median {lengths[len(lengths) // 2]}, "
          f"max {lengths[-1]}  — a feed sending only the next stop looks very different here "
          f"from one sending the whole remaining trip")

    relationships = collections.Counter(
        SCHEDULE_RELATIONSHIP.get(u["schedule_relationship"], u["schedule_relationship"])
        for u in updates)
    print(f"  trip schedule_relationship: "
          + ", ".join(f"{name} {count}" for name, count in relationships.most_common()))


def report_coverage_and_delay(updates, trips, vehicles_seen):
    """Whether it covers the lines we draw, and whether the numbers in it are plausible."""
    print("\n" + "=" * 78)
    print("3. Coverage of the monitored lines, and the delays it reports")
    print("=" * 78)

    if not trips:
        print("  Nothing could be attributed to a line — see the message above. The database is "
              "\n  the usual source; --gtfs points at a static extract instead.")
        return {}

    monitored = {u["trip_id"]: u for u in updates if u["trip_id"] in trips}
    print(f"  {len(monitored)} of {len(updates)} trip updates join the monitored lines "
          f"(trip_id joins the static extract, so the namespaces agree)")

    by_mode = collections.defaultdict(list)
    for trip_id, update in monitored.items():
        by_mode[trips[trip_id][1]].append(update)

    print(f"\n  {'mode':<8}{'trip updates':>14}{'vehicles in VP':>17}{'both':>7}"
          f"{'delay from':>14}{'median':>9}{'p90':>8}{'nonzero':>9}")
    for mode in sorted(by_mode):
        mode_updates = by_mode[mode]
        vp_trips = {t for t in vehicles_seen if trips.get(t, (None, None))[1] == mode}
        both = len(vp_trips & set(u["trip_id"] for u in mode_updates))

        delays, sources = [], collections.Counter()
        for update in mode_updates:
            value, source = observed_delay(update)
            sources[source] += 1
            if value is not None:
                delays.append(value)
        delays.sort()
        n = len(delays)
        median = f"{delays[n // 2]:.0f}s" if n else "-"
        p90 = f"{delays[min(n - 1, int(0.9 * n))]:.0f}s" if n else "-"
        nonzero = percent(sum(1 for d in delays if d != 0), n)
        source = sources.most_common(1)[0][0] if sources else "-"
        print(f"  {mode:<8}{len(mode_updates):>14}{len(vp_trips):>17}{both:>7}"
              f"{source:>14}{median:>9}{p90:>8}{nonzero:>9}")

    print("\n  'delay from' is whether TripUpdate.delay answered or a stop_time_update had to. "
          "\n  'nonzero' near 0% means the feed is echoing the timetable and is worth nothing.")
    return monitored


def report_independence(samples, trips):
    """
    Question 3, the decisive one: does a trip update carry news while the position is frozen?

    Each consecutive pair of polls is one observation of a trip, classified on two axes. The cell
    that decides the goal is FROZEN x ADVANCED — a trip update that moves when the position does not
    is information the geometry cannot reach, and the only justification for adopting this feed.
    """
    print("\n" + "=" * 78)
    print("4. Does the trip update move when the position does not?")
    print("=" * 78)

    cells = collections.defaultdict(collections.Counter)
    for trip_id, series in samples.items():
        mode = trips.get(trip_id, (None, "?"))[1]
        for before, after in zip(series, series[1:]):
            if before["pos"] is None or after["pos"] is None:
                continue
            moved = hav(before["pos"][0], before["pos"][1],
                        after["pos"][0], after["pos"][1]) > MOVED_METRES
            position = "MOVED" if moved else "FROZEN"
            if before["fingerprint"] is None or after["fingerprint"] is None:
                update = "ABSENT"
            elif before["fingerprint"] != after["fingerprint"]:
                update = "ADVANCED"
            elif after["tu_timestamp"] > before["tu_timestamp"]:
                update = "RESTAMPED"
            else:
                update = "STATIC"
            cells[mode][(position, update)] += 1

    if not cells:
        print("  No trip was seen in two consecutive polls. Raise --polls.")
        return

    print(f"  {'mode':<8}{'position':<10}{'ADVANCED':>10}{'RESTAMPED':>11}{'STATIC':>9}"
          f"{'ABSENT':>9}{'total':>8}")
    for mode in sorted(cells):
        for position in ("MOVED", "FROZEN"):
            counts = [cells[mode][(position, u)]
                      for u in ("ADVANCED", "RESTAMPED", "STATIC", "ABSENT")]
            total = sum(counts)
            if not total:
                continue
            print(f"  {mode:<8}{position:<10}" + "".join(
                f"{percent(c, total):>10}" if i == 0 else
                f"{percent(c, total):>11}" if i == 1 else
                f"{percent(c, total):>9}" for i, c in enumerate(counts)) + f"{total:>8}")

    frozen_advanced = sum(c[("FROZEN", "ADVANCED")] for c in cells.values())
    frozen_total = sum(sum(c[("FROZEN", u)] for u in ("ADVANCED", "RESTAMPED", "STATIC", "ABSENT"))
                       for c in cells.values())
    print(f"\n  FROZEN x ADVANCED overall: {frozen_advanced} of {frozen_total} frozen-position "
          f"observations ({percent(frozen_advanced, frozen_total)})")


def report_timelines(samples, trips, limit=2):
    """One train's trip updates alongside its position, because a table hides the shape of it."""
    print("\n" + "=" * 78)
    print("5. Timelines — the longest-observed trip per mode")
    print("=" * 78)

    by_mode = collections.defaultdict(list)
    for trip_id, series in samples.items():
        by_mode[trips.get(trip_id, (None, "?"))[1]].append((len(series), trip_id))

    for mode in sorted(by_mode):
        for _, trip_id in sorted(by_mode[mode], reverse=True)[:limit]:
            line = trips.get(trip_id, ("?", "?"))[0]
            print(f"\n{mode} {line}  trip {trip_id}")
            print(f"  {'wall':>9}{'pos stamp':>11}{'moved':>9}{'tu stamp':>11}{'delay':>8}"
                  f"{'next stop':>20}{'due':>10}")
            previous = None
            for sample in samples[trip_id]:
                pos = sample["pos"]
                moved = "-" if previous is None or pos is None else \
                    f"{hav(previous[0], previous[1], pos[0], pos[1]):.0f} m"
                delay = "-" if sample["delay"] is None else f"{sample['delay']:+.0f}s"
                nxt = sample["next_stop"] or "-"
                due = clock(sample["next_time"]) if sample["next_time"] else "-"
                print(f"  {clock(sample['wall']):>9}{clock(sample['pos_timestamp']):>11}{moved:>9}"
                      f"{clock(sample['tu_timestamp']):>11}{delay:>8}{nxt:>20}{due:>10}")
                if pos is not None:
                    previous = pos


VERDICT = """
  No feed, or empty, or delays all zero
      CLAUDE.md was right by accident. The geometry stays, and goal 2 falls back to SL's
      departures API — Departure already carries scheduled and expected, and GtfsTripMatchUtil
      already joins a departure row to a trip_id. Two probe stops per group cover the whole
      focus window.

  Populated, but FROZEN x ADVANCED is near zero for TRAIN
      The trip update is derived from the same position report we already have, so it inherits
      the same blind spot and cannot fix the sawtooth. Not worth a second feed in the poll loop.
      Prefer fixing the geometry instead — measuring delay at the last passed stop rather than
      interpolating removes the bias without any new dependency.

  Populated, and FROZEN x ADVANCED is substantial for TRAIN
      Adopt it. The delay becomes an observation rather than a reconstruction, joined on the
      trip_id we already use, and both hard dependencies in goal 2 dissolve: delay is naturally
      absent rather than an ambiguous zero, and the mid-segment bias never arises. Poll it
      alongside VehiclePositions in the same cycle — 24 calls/minute against the ~46 the quota
      sustains.

  Delays plausible but stop_time_update absent or one stop deep
      Enough for lateness, not enough to replace the per-stop countdowns. Keep the timetable
      plus a corrected delay, and let this feed supply the correction.
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--polls", type=int, default=16)
    parser.add_argument("--interval", type=int, default=20,
                        help="seconds between polls; polls*interval should exceed one train "
                             "segment (line 43 segments run 3-6 minutes)")
    parser.add_argument("--probe-only", action="store_true",
                        help="answer question 1 and stop, without the polling run")
    parser.add_argument("--static-source", choices=("auto", "db", "files"), default="auto",
                        help="where to attribute trips to lines from; auto tries the database first")
    parser.add_argument("--gtfs", default=GTFS,
                        help="unzipped static GTFS extract, used when the database cannot answer")
    parser.add_argument("--props", default=str(PROPS),
                        help="properties file to read the realtime key and datasource from")
    args = parser.parse_args()

    key = read_key(args.props)
    _, updates = probe(key)
    report_field_availability(updates, f"{len(updates)} trip updates, all lines")

    if args.probe_only:
        print("\n--probe-only: stopping before the polling run. Questions 3 and 4 are unanswered.")
        return

    print()
    trips = load_monitored_trips(args)
    print()

    # samples[trip_id] = one dict per poll, carrying both feeds' view of that trip at that moment.
    samples = collections.defaultdict(list)
    vehicles_seen = set()
    latest_updates = []

    for poll in range(args.polls):
        if poll:
            time.sleep(args.interval)
        wall = time.time()
        try:
            _, raw_vp = fetch_raw(VEHICLE_POSITIONS_URL, key)
            _, raw_tu = fetch_raw(TRIP_UPDATES_URL, key)
            positions = parse_vehicle_positions(raw_vp)
            _, poll_updates = parse_trip_updates(raw_tu)
        except (urllib.error.URLError, OSError, ValueError, IndexError) as e:
            print(f"poll {poll}: fetch failed: {scrub(e, key)}")
            continue

        by_trip = {u["trip_id"]: u for u in poll_updates if u["trip_id"]}
        latest_updates = poll_updates

        monitored_positions = 0
        for vp in positions:
            trip_id = vp["trip_id"]
            if trip_id not in trips:
                continue
            monitored_positions += 1
            vehicles_seen.add(trip_id)
            update = by_trip.get(trip_id)
            delay = observed_delay(update)[0] if update else None
            head = update["stop_updates"][0] if update and update["stop_updates"] else None
            samples[trip_id].append({
                "wall": wall,
                "pos": (vp["lat"], vp["lng"]),
                "pos_timestamp": vp["timestamp"],
                "tu_timestamp": update["timestamp"] if update else 0,
                "fingerprint": update_fingerprint(update) if update else None,
                "delay": delay,
                "next_stop": head["stop_id"] if head else None,
                "next_time": (head["departure_time"] or head["arrival_time"]) if head else None,
            })

        matched = sum(1 for t in trips if t in by_trip)
        print(f"poll {poll:2d} at {clock(wall)}  {len(positions):4d} positions "
              f"({monitored_positions:3d} monitored), {len(poll_updates):4d} trip updates "
              f"({matched:3d} monitored)")

    if not samples:
        sys.exit("\nNo monitored vehicles seen. If the static extract is stale the trip ids will "
                 "not join — re-run when the backend has parsed a current feed.")

    report_coverage_and_delay(latest_updates, trips, vehicles_seen)
    report_independence(samples, trips)
    report_timelines(samples, trips)

    print("\n" + "=" * 78)
    print("Reading it")
    print("=" * 78)
    print(VERDICT)


if __name__ == "__main__":
    main()
