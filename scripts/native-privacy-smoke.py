#!/usr/bin/env python3
"""Read and exercise the installed app's pre-login notices on the disposable CI emulator."""
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

kind = sys.argv[1]
if kind not in {"patient", "staff"}:
    raise SystemExit("Expected patient or staff")
output = Path("native-qa")
package = f"com.aistudio.chettinadcare.{kind}.cwqtxr"


def adb(*arguments, binary=False):
    return subprocess.check_output(["adb", *arguments], text=not binary)


def observe(label):
    adb("shell", "uiautomator", "dump", "/sdcard/privacy-window.xml")
    xml = adb("shell", "cat", "/sdcard/privacy-window.xml")
    (output / f"{kind}-{label}-window.xml").write_text(xml)
    return ET.fromstring(xml)


def bounds(node):
    values = [int(v) for v in re.findall(r"\d+", node.get("bounds", ""))]
    return values if len(values) == 4 else [0, 0, 0, 0]


def visible_text(tree, text, prefix=False):
    for node in tree.iter("node"):
        value = node.get("text", "")
        match = value.startswith(text) if prefix else value == text
        x1, y1, x2, y2 = bounds(node)
        if match and node.get("package") == package and x2 > x1 and y2 > y1:
            return node
    return None


def find(text, label, prefix=False, scroll=False):
    for _ in range(8 if scroll else 3):
        tree = observe(label)
        node = visible_text(tree, text, prefix)
        if node is not None:
            return node
        if scroll:
            panels = [n for n in tree.iter("node") if n.get("scrollable") == "true" and n.get("package") == package]
            if not panels:
                break
            panel = max(panels, key=lambda n: (bounds(n)[2] - bounds(n)[0]) * (bounds(n)[3] - bounds(n)[1]))
            x1, y1, x2, y2 = bounds(panel)
            x = (x1 + x2) // 2
            adb("shell", "input", "swipe", str(x), str(y1 + (y2-y1)*4//5), str(x), str(y1 + (y2-y1)//5), "350")
    raise AssertionError(f"{kind}: expected native text was not reachable: {text}")


def tap(text, label, scroll=False):
    x1, y1, x2, y2 = bounds(find(text, label, scroll=scroll))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))


def screenshot(label):
    (output / f"{kind}-{label}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))


tap("Privacy", "login-privacy", scroll=True)
find("Chettinad Medical Sciences", "privacy")
find("Support: satyanarayanr17@gmail.com", "privacy")
find("Your hospital profile and care", "privacy")
screenshot("privacy")
find("Your choices", "privacy-bottom", scroll=True)
tap("Close", "privacy-close")
tap("Help and access", "login-help", scroll=True)
find("Chettinad Medical Sciences", "help")
find("Support: satyanarayanr17@gmail.com", "help")
screenshot("help")
find("This app is not a medical device", "help-bottom", prefix=True, scroll=True)
tap("Close", "help-close")
find("Privacy", "notices-dismissed", scroll=True)
print(f"PASS {kind}: native pre-login Privacy and Help open, contain publisher/support, scroll and dismiss.")
