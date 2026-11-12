#!/bin/bash
# Drives the phone over adb by on-screen text. Usage:
#   tools/ui.sh find "<text>" | tap "<text>" | long "<text>" | shot <file.png> | dump
# Needs adb on PATH and one device connected.
set -u
OUT="${TMPDIR:-/tmp}/slate-ui"
mkdir -p "$OUT"
center() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
  python3 -I - "$1" "$OUT/ui.xml" <<'PY'
import sys, re, xml.etree.ElementTree as ET
t = sys.argv[1]
for n in ET.parse(sys.argv[2]).getroot().iter('node'):
    if n.get('text') == t or n.get('content-desc') == t:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds'))); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
}
case "${1:-}" in
  find) center "$2";;
  tap) read -r x y < <(center "$2"); [ -z "${x:-}" ] && { echo "not found: $2"; exit 1; }; adb shell input tap "$x" "$y";;
  long) read -r x y < <(center "$2"); [ -z "${x:-}" ] && { echo "not found: $2"; exit 1; }; adb shell input swipe "$x" "$y" "$x" "$y" 700;;
  shot) adb exec-out screencap -p > "$2"; echo "$2";;
  dump) adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"\|content-desc="[^"]*"' | grep -v '=""';;
  *) echo "usage: $0 find|tap|long <text> | shot <file> | dump"; exit 2;;
esac
