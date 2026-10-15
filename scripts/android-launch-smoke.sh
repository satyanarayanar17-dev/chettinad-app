#!/usr/bin/env bash
set -euo pipefail
mkdir -p native-qa
adb install -r native-apks/patient/app-patient-demo-debug.apk
adb install -r native-apks/staff/app-staff-demo-debug.apk
for kind in patient staff; do
  package="com.aistudio.chettinadcare.${kind}.cwqtxr"
  adb shell am force-stop "$package"
  adb shell am start -W -n "$package/com.example.$kind.MainActivity" > "native-qa/$kind-launch.txt"
  found=false
  for attempt in {1..10}; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml "native-qa/$kind-window.xml" >/dev/null
    if grep -Eq 'Mobile number|Password / PIN' "native-qa/$kind-window.xml"; then found=true; break; fi
    sleep 2
  done
  test "$found" = true
  adb exec-out screencap -p > "native-qa/$kind-login.png"
  adb shell pidof "$package" > "native-qa/$kind-pid.txt"
done
adb shell pm list packages | grep -E 'com.aistudio.chettinadcare.(patient|staff).cwqtxr' > native-qa/coexisting-packages.txt
test "$(wc -l < native-qa/coexisting-packages.txt)" = 2
