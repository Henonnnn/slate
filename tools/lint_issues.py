#!/usr/bin/env python3
"""Counts lint issues in the debug report. Lint only fails the build on errors, and its XML
puts each <issue on its own lines, so grep can't count them. Run after ./gradlew lint."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

report = Path(__file__).resolve().parent.parent / "app/build/reports/lint-results-debug.xml"
if not report.exists():
    sys.exit(f"No report at {report}; run ./gradlew lint first.")
issues = ET.parse(report).getroot().findall("issue")
print(f"lint issues: {len(issues)}")
for issue in issues:
    loc = issue.find("location")
    where = f"{Path(loc.get('file', '')).name} {loc.get('line', '')}" if loc is not None else ""
    print(f"{issue.get('severity')} {issue.get('id')} {where}: {issue.get('message', '')[:120]}")
sys.exit(1 if issues else 0)
