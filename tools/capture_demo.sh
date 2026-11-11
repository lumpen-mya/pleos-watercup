#!/usr/bin/env bash
# 에뮬레이터에 디버그 APK를 설치하고 시연 화면·영상을 다시 만든다.
# 사용: tools/capture_demo.sh [기기 serial, 기본 emulator-5580]
# 결과: docs/screenshots/01~05, docs/demo/watercup-demo.mp4
set -u
cd "$(dirname "$0")/.."
SERIAL="${1:-emulator-5580}"
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
adb() { "$ADB" -s "$SERIAL" "$@"; }
shot() { adb exec-out screencap -p > "docs/screenshots/$1"; echo "  캡처 $1"; }
wait_s() { python3 -c "import time; time.sleep($1)"; }

mkdir -p docs/screenshots docs/demo
adb install -r app/build/outputs/apk/debug/app-debug.apk | tail -1
adb shell pm grant --user 10 app.watercup android.car.permission.CAR_SPEED
adb forward tcp:8088 tcp:8088 > /dev/null
python3 tools/drive_demo.py park > /dev/null
adb shell pm clear --user 10 app.watercup > /dev/null   # 기록 초기화
adb shell pm grant --user 10 app.watercup android.car.permission.CAR_SPEED
adb shell am start --user 10 -n app.watercup/.MainActivity > /dev/null
wait_s 6

echo "거친 주행 (녹화)"
adb shell rm -f /sdcard/watercup-demo.mp4
adb shell screenrecord --time-limit 50 --size 1280x720 --bit-rate 4000000 /sdcard/watercup-demo.mp4 &
REC=$!
wait_s 2
python3 tools/drive_demo.py rough > /dev/null &
wait_s 13; shot 03-driving-spill.png
wait_s 30; shot 05-review-rough.png
wait "$REC"
adb pull /sdcard/watercup-demo.mp4 docs/demo/watercup-demo.mp4 | tail -1

echo "조금 나아진 주행 (기록용)"
python3 tools/drive_demo.py mild > /dev/null
wait_s 3

echo "부드러운 주행"
python3 tools/drive_demo.py smooth > /dev/null &
wait_s 12; shot 02-driving-smooth.png
wait_s 30; shot 04-review-clean.png

echo "대기 화면"
adb shell am force-stop --user 10 app.watercup
adb shell am start --user 10 -n app.watercup/.MainActivity > /dev/null
wait_s 6; shot 01-ready.png
echo "끝"
