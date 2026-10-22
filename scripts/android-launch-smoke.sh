#!/usr/bin/env bash
set -euo pipefail
mkdir -p native-qa
kind=setup
capture_failure() {
  result=$?
  if [ "$result" -ne 0 ]; then
    adb exec-out screencap -p > "native-qa/$kind-failure.png" || true
    adb logcat -d -t 500 > "native-qa/$kind-logcat.txt" || true
    adb shell dumpsys activity activities > "native-qa/$kind-activities.txt" || true
  fi
  exit "$result"
}
trap capture_failure EXIT

adb install -r native-apks/patient/app-patient-demo-debug.apk
adb install -r native-apks/staff/app-staff-demo-debug.apk
for kind in patient staff; do
  package="com.aistudio.chettinadcare.${kind}.cwqtxr"
  if [ "$kind" = patient ]; then
    title="Chettinad Care Patient Access"
    field="Mobile number"
    action="Send code"
  else
    title="Chettinad Care Staff"
    field="Password / Secure PIN"
    action="Sign In"
  fi
  adb shell am force-stop "$package"
  adb shell am start -W -n "$package/com.example.$kind.MainActivity" > "native-qa/$kind-launch.txt"
  grep -Fq 'Status: ok' "native-qa/$kind-launch.txt"
  found=false
  for attempt in {1..10}; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb pull /sdcard/window.xml "native-qa/$kind-window.xml" >/dev/null
    if grep -Fq "package=\"$package\"" "native-qa/$kind-window.xml" &&
       grep -Fq "text=\"$title\"" "native-qa/$kind-window.xml" &&
       grep -Fq "text=\"$field\"" "native-qa/$kind-window.xml" &&
       grep -Fq "text=\"$action\"" "native-qa/$kind-window.xml"; then
      found=true
      break
    fi
    sleep 2
  done
  if [ "$found" != true ]; then
    echo "Expected $kind login screen was not visible after launch." >&2
    exit 1
  fi
  adb exec-out screencap -p > "native-qa/$kind-login.png"
  adb shell pidof "$package" > "native-qa/$kind-pid.txt"
  test -s "native-qa/$kind-pid.txt"
done
adb shell pm list packages | tr -d '\r' > native-qa/installed-packages.txt
for kind in patient staff; do
  grep -Fx "package:com.aistudio.chettinadcare.${kind}.cwqtxr" native-qa/installed-packages.txt
done > native-qa/coexisting-packages.txt
