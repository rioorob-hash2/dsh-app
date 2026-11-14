"""Install the current APK, reset the app, log in on the phone and screenshot."""

import os
import re
import subprocess
import time

ADB = r"C:\Android\adb.exe"
PROJ = r"C:\Users\rioor\Documents\DeepSeek Harness\DshApp"
APK = os.path.join(PROJ, "dist", "DSH-1.2.apk")
SHOT = os.path.join(PROJ, "screenshots", "04-login-fixed.png")
REPORT = r"C:\Users\rioor\Documents\DeepSeek Harness\dsh-cloud-REPORT.md"


def sh(*args):
    return subprocess.run([ADB, "shell", *args], capture_output=True, text=True).stdout


def dump_ui():
    sh("uiautomator", "dump", "/sdcard/ui.xml")
    return sh("cat", "/sdcard/ui.xml")


def center(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


subprocess.run([ADB, "install", "-r", APK], capture_output=True)
sh("pm", "clear", "com.brotik.dshcloud")
sh("am", "start", "-n", "com.brotik.dshcloud/.MainActivity")
time.sleep(3)

ui = dump_ui()
pwd_node = re.search(r'text="Пароль"[^>]*class="android\.widget\.EditText"[^>]*bounds="(\[[^"]+\])"', ui)
if not pwd_node:
    nodes = re.findall(r'<node[^>]*password="true"[^>]*bounds="(\[[^"]+\])"', ui)
    bounds = nodes[0] if nodes else None
else:
    bounds = pwd_node.group(1)
if not bounds:
    raise SystemExit("password field not found in UI dump")

text = open(REPORT, encoding="utf-8").read()
pwd = re.search(r"Пароль basic auth \| `([^`]+)`", text).group(1)

x, y = center(bounds)
sh("input", "tap", str(x), str(y))
time.sleep(0.8)
sh("input", "text", pwd)
time.sleep(0.6)
sh("input", "keyevent", "4")
time.sleep(0.5)

ui = dump_ui()
save = re.search(r'text="Сохранить"[^>]*bounds="(\[[^"]+\])"', ui)
sx, sy = center(save.group(1))
sh("input", "tap", str(sx), str(sy))
time.sleep(9)

sh("screencap", "-p", "/sdcard/shot.png")
subprocess.run([ADB, "pull", "/sdcard/shot.png", SHOT], capture_output=True)
print("password field at", (x, y), "| save at", (sx, sy), "| shot:", os.path.exists(SHOT))
