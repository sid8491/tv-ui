#!/usr/bin/env bash
# Deploys the launcher to the TV WITHOUT making it the home screen. Run from this folder, only with Sid's go-ahead.
#   deploy.sh            install + grant permissions + push photos
#   deploy.sh --launch   ...and open it on top of the current home (BACK/HOME on the remote returns to Projectivy)
#   deploy.sh --sethome  ...and make it the default home (undo: restore.cmd or the line in changes.log)
export MSYS_NO_PATHCONV=1
ADB="C:/Users/Sid/AppData/Local/Android/Sdk/platform-tools/adb.exe"
# the TV's adb address comes from TV_ADDR or tv.properties (addr=...) in the repo root, e.g. 192.168.1.50:5555
TV="${TV_ADDR:-$(sed -n "s/^addr=//p" ../tv.properties 2>/dev/null)}"; [ -n "$TV" ] || { echo "set TV_ADDR or create ../tv.properties (addr=<ip>:5555)"; exit 1; }
PHOTOS="D:/work/develop/debloat-tv/design/photos/tv-ready"
ICONS="D:/work/develop/debloat-tv/design/photos/icons-ready"
A() { "$ADB" -s "$TV" shell "$@" 2>&1 </dev/null | grep -v 'Init wrapper' | tr -d '\r'; }
"$ADB" connect "$TV" >/dev/null 2>&1
"$ADB" -s "$TV" get-state >/dev/null 2>&1 || { echo "TV not reachable"; exit 1; }

echo "== photos -> /sdcard/Pictures/Aanya"
A "mkdir -p /sdcard/Pictures/Aanya"
for f in "$PHOTOS"/*.jpg "$PHOTOS"/*.jpeg "$PHOTOS"/*.png; do
  [ -f "$f" ] || continue
  "$ADB" -s "$TV" push "$f" "/sdcard/Pictures/Aanya/" </dev/null 2>&1 | grep -v 'Init wrapper' | tail -1
done
A "ls /sdcard/Pictures/Aanya | wc -l"

echo "== icon photos -> /sdcard/Pictures/Aanya/icons (one per dock app, with the app's banner as a badge)"
A "mkdir -p /sdcard/Pictures/Aanya/icons"
for f in "$ICONS"/*.jpg "$ICONS"/*.jpeg "$ICONS"/*.png "$ICONS"/crops.txt; do
  [ -f "$f" ] || continue
  "$ADB" -s "$TV" push "$f" "/sdcard/Pictures/Aanya/icons/" </dev/null 2>&1 | grep -v 'Init wrapper' | tail -1
done
A "ls /sdcard/Pictures/Aanya/icons | wc -l"

echo "== install"
"$ADB" -s "$TV" install -r launcher.apk 2>&1 </dev/null | grep -v 'Init wrapper'
A "pm grant in.sid.launcher android.permission.READ_EXTERNAL_STORAGE; pm grant in.sid.launcher android.permission.READ_TV_LISTINGS"
A "dumpsys package in.sid.launcher | grep -E 'versionName|granted=true'"

if [ "$1" = "--launch" ]; then
  A "am start -n in.sid.launcher/.MainActivity"
fi
if [ "$1" = "--sethome" ]; then
  A "cmd package set-home-activity --user 0 in.sid.launcher/.MainActivity"
  A "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME" | tail -1
fi
