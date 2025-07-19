"""Build a local daily report from the primary AFS CMS application logs in a ZIP."""

import argparse
from collections import Counter, defaultdict
from datetime import date
import json
import os
from pathlib import Path, PurePosixPath
import re
import tempfile
from zipfile import ZipFile

PRIMARY_LOG = re.compile(r"app\.log(?:\.\d{4}-\d{2}-\d{2}\.\d+(?:\.gz)?)?")
HEADER = re.compile(rb"^\[(TRACE|DEBUG|INFO|WARN|ERROR) (\d{4}-\d{2}-\d{2}) ")
LEVELS = ("trace", "debug", "info", "warn", "error")


def primary_logs(archive):
    for entry in archive.infolist():
        path = PurePosixPath(entry.filename.replace("\\", "/"))
        if entry.is_dir() or path.parent.name not in ("log", "logs"):
            continue
        if PRIMARY_LOG.fullmatch(path.name):
            yield entry


def aggregate(path):
    daily = defaultdict(Counter)
    count = 0
    with ZipFile(path) as archive:
        fingerprints = set()
        for entry in primary_logs(archive):
            fingerprint = (entry.CRC, entry.file_size)
            if fingerprint in fingerprints:
                continue
            fingerprints.add(fingerprint)
            count += 1
            with archive.open(entry) as member:
                if entry.filename.endswith(".gz"):
                    import gzip
                    with gzip.GzipFile(fileobj=member) as log:
                        count_events(log, daily)
                else:
                    count_events(member, daily)
    if not daily:
        raise ValueError("No timestamped events found in the primary application logs")
    return {
        "files": count,
        "days": [{"date": day, **{level: counts[level] for level in LEVELS}}
                 for day, counts in sorted(daily.items())],
    }


def count_events(log, daily):
    for line in log:
        match = HEADER.match(line)
        if match is None:
            continue
        severity, timestamp = (value.decode("ascii") for value in match.groups())
        if timestamp not in daily:
            try:
                date.fromisoformat(timestamp)
            except ValueError:
                continue
        daily[timestamp][severity.lower()] += 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "data/imported-log-report.json")
    args = parser.parse_args()
    result = aggregate(args.archive)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=args.output.parent, delete=False) as temporary:
        json.dump(result, temporary, ensure_ascii=False, indent=2)
        temporary.write("\n")
    os.replace(temporary.name, args.output)
    days = result["days"]
    print(f"Imported {result['files']} primary logs and {len(days)} days ({days[0]['date']} to {days[-1]['date']}).")


if __name__ == "__main__":
    main()

