#!/usr/bin/env python3
"""
Is the bus data missing upstream, or are we losing it?

Written the day buses disappeared from both the departures pane and the live traffic view while trains
and the metro kept working. Two symptoms at once on one mode is the shape of an upstream change, but
"the API has no buses" is a claim that has to be tested rather than assumed — the same mistake this
project made for months about the TripUpdates feed existing.

So this asks the question from four sides, and the four together separate the cases:

  1. SL departures API   — a public, key-free API the browser calls directly. Independent of everything
                           else here, including our database. If buses are missing from a stop that has
                           them, the fault is upstream of every line of our code.
  2. VehiclePositions    — do any monitored bus trips report a position at all?
  3. TripUpdates         — same question of the other realtime feed.
  4. our static tables   — do we still HOLD bus routes and trips? If the parse dropped them, the two
                           realtime sections above would look empty for a reason that is entirely ours.

Section 4 is the one that keeps this honest. Empty realtime results prove nothing on their own: a feed
with no buses in it and a feed full of buses we can no longer recognise look identical from inside the
application.

Conventions, shared with the other scripts here: no dependencies, secrets from the environment first
and only then from the gitignored properties file, never printed and scrubbed from errors. This
repository is public.

    ./bus_data_check.py                 # every section, against the default stop
    ./bus_data_check.py --stop Alvik    # resolve another stop by name and check its departures
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from rt_tripupdates import (  # noqa: E402  — the path insert above has to come first
    PROPS,
    TRIP_UPDATES_URL,
    VEHICLE_POSITIONS_URL,
    explain_http_error,
    fetch_raw,
    load_monitored_trips_from_db,
    parse_trip_updates,
    parse_vehicle_positions,
    read_datasource,
    read_key,
    scrub,
)

# Skogslöparvägen — the default home stop, and the one the reported symptom was seen at. The departures
# endpoint takes the last four characters of the 16-character site id.
DEFAULT_SITE = "3715"

DEPARTURES_URL = "https://transport.integration.sl.se/v1/sites/{site}/departures"
# The exact query the app's own stop autocomplete uses. `type_sf=any` is not optional — without it the
# endpoint answers 400, which reads as "no such stop" rather than as a malformed request.
STOP_FINDER_URL = ("https://journeyplanner.integration.sl.se/v2/stop-finder"
                   "?name_sf={name}&any_obj_filter_sf=2&type_sf=any")

# What we monitor, so a mode that is simply not served here is not read as a mode that vanished.
MODES = ("BUS", "TRAIN", "METRO")

STATIC_SQL = """
  SELECT m.transport_mode,
         COUNT(DISTINCT r.route_id) AS routes,
         COUNT(DISTINCT t.trip_id)  AS trips
    FROM gtfs_monitored_route m
    LEFT JOIN gtfs_route r
           ON r.route_short_name REGEXP CONCAT('^', m.route_short_name, '[A-Za-z]?$')
    LEFT JOIN gtfs_trip t ON t.route_id = r.route_id
   GROUP BY m.transport_mode
   ORDER BY m.transport_mode
"""

DOWNLOAD_LOG_SQL = """
  SELECT * FROM gtfs_download_log ORDER BY 1 DESC LIMIT 5
"""


def rule(title):
    print()
    print("=" * 78)
    print(title)
    print("=" * 78)


def get_json(url):
    """A public SL API, decoded. No key is involved in any call this function makes."""
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode())


def query(datasource, sql):
    """
    Rows from the development database, as lists of strings.

    The password goes into a mode-600 defaults file rather than onto the command line or into the
    environment, so it is never visible in `ps` — the same handling `load_monitored_trips_from_db`
    uses, and the reason this shells out to the mysql client instead of importing a driver.
    """
    if datasource is None:
        return None, "no usable spring.datasource.* in the properties file or the environment"
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
             "-D", datasource["schema"], "-e", sql],
            capture_output=True, text=True, timeout=60, check=False)
    except FileNotFoundError:
        return None, "no mysql client on PATH"
    except subprocess.SubprocessError as e:
        return None, f"mysql failed: {scrub(e, datasource['password'])}"
    finally:
        if defaults:
            try:
                os.unlink(defaults)
            except OSError:
                pass
    if result.returncode != 0:
        detail = result.stderr.strip().splitlines()[-1] if result.stderr.strip() else "no output"
        return None, f"query failed: {scrub(detail, datasource['password'])}"
    return [line.split("\t") for line in result.stdout.splitlines()], None


# ----------------------------------------------------------------------------- 1. departures

def resolve_site(name):
    """A stop name to the site id the departures endpoint wants, via the key-free stop finder."""
    payload = get_json(STOP_FINDER_URL.format(name=urllib.parse.quote(name)))
    for location in payload.get("locations", payload if isinstance(payload, list) else []):
        site_id = str(location.get("id", ""))
        if site_id:
            return site_id[-4:], location.get("disassembledName") or location.get("name")
    return None, None


def check_departures(site, label, verbose=False):
    rule(f"1. SL departures API — site {site}{f' ({label})' if label else ''}")
    print("   Public and key-free: this is the call the browser makes, with nothing of ours in the way.")
    try:
        payload = get_json(DEPARTURES_URL.format(site=site))
    except Exception as e:  # noqa: BLE001 — any failure here is itself the answer
        print(f"   FAILED: {e}")
        return
    departures = payload.get("departures", [])
    modes = Counter(d.get("line", {}).get("transport_mode", "?") for d in departures)
    print(f"   {len(departures)} departures")
    if not departures:
        print("   -> the stop reports nothing at all for any mode. Time of day, or an outage.")
        return
    for mode, count in sorted(modes.items()):
        lines = sorted({str(d["line"].get("designation")) for d in departures
                        if d.get("line", {}).get("transport_mode") == mode})
        print(f"     {mode:<8} {count:>3}   lines: {', '.join(lines)}")
    if "BUS" not in modes:
        print("   -> NO BUS DEPARTURES AT ALL. Upstream, and nothing to do with this application.")
        return

    # Rows existing is not the same as rows having live data behind them, and the difference is the
    # whole diagnosis. A departure still an hour out is legitimately "EXPECTED"; a line where NOTHING
    # is ever en route, at a stop where other lines are, is a line SL has stopped tracking.
    print("\n   live coverage per bus line — 'en route' means SL has a fix on the vehicle:")
    per_line = {}
    for departure in departures:
        line = departure.get("line", {})
        if line.get("transport_mode") != "BUS":
            continue
        rows, moving = per_line.get(str(line.get("designation")), (0, 0))
        state = departure.get("journey", {}).get("state", "")
        per_line[str(line.get("designation"))] = (rows + 1, moving + (1 if "PROGRESS" in state else 0))
    dark = []
    for line, (rows, moving) in sorted(per_line.items()):
        flag = ""
        if moving == 0:
            flag = "   <-- nothing en route"
            dark.append(line)
        print(f"     {line:<6} {rows:>2} departures, {moving:>2} en route{flag}")
    live = [line for line, (_, moving) in per_line.items() if moving]
    if dark and live:
        print(f"   -> {', '.join(live)} have live vehicles here and {', '.join(dark)} have none, at the")
        print("      same stop at the same moment. That is SL tracking some bus lines and not others.")
    elif dark:
        print("   -> no bus line here has a vehicle en route. Could be the hour; compare a busier stop")
        print("      with --stop before concluding anything.")
    if not verbose:
        return
    for departure in departures:
        line = departure.get("line", {})
        if line.get("transport_mode") != "BUS":
            continue
        journey = departure.get("journey", {})
        print(f"     {departure.get('scheduled', '?')[-8:]}  {line.get('designation'):<5}"
              f"  {departure.get('destination', '?'):<24}  state={journey.get('state', '?')}"
              f"  prediction={journey.get('prediction_state', '-')}")


# ----------------------------------------------------------------------------- 2/3. realtime

def check_realtime(url, name, parse, trips, key):
    rule(f"{name}")
    try:
        status, raw = fetch_raw(url, key)
    except urllib.error.HTTPError as e:
        print(f"   HTTP {e.code}: {explain_http_error(e, key)}")
        return
    except Exception as e:  # noqa: BLE001
        print(f"   FAILED: {scrub(e, key)}")
        return
    entries = parse(raw)[1] if name.startswith("3") else parse(raw)
    print(f"   HTTP {status}, {len(entries)} entries in the feed")

    per_mode = Counter()
    per_line = Counter()
    unknown = 0
    for entry in entries:
        trip_id = entry.get("trip_id") or ""
        known = trips.get(trip_id)
        if known is None:
            unknown += 1
            continue
        line, mode = known
        per_mode[mode] += 1
        per_line[(mode, line)] += 1

    if not trips:
        print("   (no monitored trips loaded, so nothing can be attributed — see section 4)")
        return
    print(f"   {sum(per_mode.values())} on monitored lines, {unknown} on lines we do not track")
    for mode in MODES:
        count = per_mode.get(mode, 0)
        lines = ", ".join(f"{line}:{n}" for (m, line), n in sorted(per_line.items()) if m == mode)
        flag = "   <-- NOTHING" if count == 0 else ""
        print(f"     {mode:<8} {count:>4}   {lines}{flag}")


# ----------------------------------------------------------------------------- 4. our static data

def check_static(datasource):
    rule("4. Our own static tables — do we still hold bus routes and trips?")
    print("   Empty realtime results above prove nothing on their own: a feed with no buses and a feed")
    print("   full of buses we can no longer recognise look identical from inside the application.")
    rows, error = query(datasource, STATIC_SQL)
    if error:
        print(f"   unavailable: {error}")
        return
    print("   mode      routes   trips")
    for row in rows:
        flag = "   <-- NOTHING PARSED" if row[2] in ("0", "NULL") else ""
        print(f"     {row[0]:<8} {row[1]:>6} {row[2]:>7}{flag}")

    rows, error = query(datasource, DOWNLOAD_LOG_SQL)
    if error:
        print(f"\n   pipeline log unavailable: {error}")
        return
    print("\n   most recent pipeline runs (local profile writes future-dated placeholder rows —")
    print("   they suppress the next ten days of downloads and are not stale data):")
    for row in rows:
        print(f"     {row[0]}  {row[1]:<12} {row[2][:60]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--site", default=DEFAULT_SITE, help="site id for the departures check")
    parser.add_argument("--stop", help="resolve a stop by name instead, via the public stop finder")
    parser.add_argument("--props", default=str(PROPS), help="path to the local properties file")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="list every bus departure with its journey state")
    parser.add_argument("--skip-realtime", action="store_true",
                        help="departures and static tables only — no realtime key needed")
    args = parser.parse_args()

    site, label = args.site, None
    if args.stop:
        try:
            site, label = resolve_site(args.stop)
        except Exception as e:  # noqa: BLE001
            sys.exit(f"Could not resolve {args.stop!r}: {e}")
        if site is None:
            sys.exit(f"No stop matched {args.stop!r}")

    check_departures(site, label, verbose=args.verbose)

    datasource = read_datasource(args.props)
    trips, source = ({}, "skipped")
    if not args.skip_realtime:
        trips, source = load_monitored_trips_from_db(datasource)
        key = read_key(args.props)
        print(f"\n(monitored trips loaded from {source})")
        check_realtime(VEHICLE_POSITIONS_URL, "2. GTFS-RT VehiclePositions", parse_vehicle_positions,
                       trips, key)
        check_realtime(TRIP_UPDATES_URL, "3. GTFS-RT TripUpdates", parse_trip_updates, trips, key)

    check_static(datasource)

    rule("Reading this")
    print("   buses in section 1 but not in 2/3  -> the realtime feed has dropped them; ours is fine")
    print("   no buses anywhere, trips in 4      -> upstream, on both APIs at once")
    print("   no buses anywhere, nothing in 4    -> OUR parse lost them; look at the pipeline log")
    print("   buses in 2/3 but not on screen     -> not the data. A frontend or serving problem.")
    print()
    print("   Section 4 reads the DEVELOPMENT database. The app you are looking at runs against")
    print("   production on the Mac Mini, and the two can hold different static extracts.")


if __name__ == "__main__":
    import urllib.error
    import urllib.parse
    main()
