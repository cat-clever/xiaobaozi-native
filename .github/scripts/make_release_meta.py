# -*- coding: utf-8 -*-
"""生成 SideStore Source JSON、更新清单（latest.json）与 Release 说明。"""
import glob
import json
import os
import re
import subprocess
from datetime import datetime, timezone

TAG = os.environ.get("TAG", "v1.0.0")
REPO = os.environ.get("REPO", "cat-clever/xiaobaozi-native")
VERSION = TAG.lstrip("v")


def android_version_code():
    path = "android/app/build.gradle"
    with open(path, encoding="utf-8") as f:
        m = re.search(r"versionCode\s+(\d+)", f.read())
    if not m:
        raise SystemExit(f"读不到 {path} 里的 versionCode")
    return int(m.group(1))


def changelog():
    rng = ["-n", "12"]
    try:
        prev = subprocess.run(
            ["git", "describe", "--abbrev=0", "--tags", f"{TAG}^"],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
        if prev:
            rng = [f"{prev}..{TAG}"]
    except Exception:
        pass

    out = subprocess.run(
        ["git", "log", "--pretty=format:%s", *rng],
        capture_output=True, text=True, check=True,
    ).stdout

    lines = []
    for line in out.splitlines():
        line = line.strip()
        if not line or line.startswith(("chore:", "docs:", "Merge ")):
            continue
        lines.append(line)
    return lines


CHANGES = changelog()

ipas = sorted(glob.glob("dist/*.ipa"))
apks = sorted(glob.glob("dist/*.apk"))
if not ipas:
    raise SystemExit("没有找到 IPA 产物")
if not apks:
    raise SystemExit("没有找到 APK 产物")

ipa = ipas[0]
ipa_name = os.path.basename(ipa)
apk = apks[0]
apk_name = os.path.basename(apk)

release_base = f"https://github.com/{REPO}/releases/download/{TAG}"
page_url = f"https://github.com/{REPO}/releases/tag/{TAG}"
latest_base = f"https://github.com/{REPO}/releases/latest/download"
published_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

source = {
    "name": "小包子影视",
    "identifier": "com.xiaobaozi.native.source",
    "apps": [
        {
            "name": "小包子影视",
            "bundleIdentifier": "com.xiaobaozi.video.ios",
            "developerName": "xiaobaozi",
            "subtitle": "端内双源直连的纯原生流媒体客户端",
            "localizedDescription": "纯 SwiftUI / Java 原生实现：请求签名、响应解密、线路解析与 CDN 取流全部在设备上完成。",
            "version": VERSION,
            "versionDate": published_at,
            "downloadURL": f"{release_base}/{ipa_name}",
            "size": os.path.getsize(ipa),
            "minOSVersion": "16.0",
        }
    ],
}

os.makedirs("source", exist_ok=True)
with open("source/xiaobaozi.json", "w", encoding="utf-8") as f:
    json.dump(source, f, ensure_ascii=False, indent=2)

manifest = {
    "version": VERSION,
    "versionCode": android_version_code(),
    "publishedAt": published_at,
    "notes": CHANGES,
    "android": {
        "url": f"{release_base}/{apk_name}",
        "size": os.path.getsize(apk),
    },
    "ios": {
        "url": f"{release_base}/{ipa_name}",
        "size": os.path.getsize(ipa),
        "source": f"{latest_base}/xiaobaozi.json",
    },
    "page": page_url,
}
with open("source/latest.json", "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)

changes_md = "\n".join(f"- {line}" for line in CHANGES) or "- 初始纯原生版本发布"
notes = f"""## 小包子影视 {TAG}

纯原生分离版（iOS SwiftUI + Android Java）。

### 本版改动

{changes_md}

### 产物

| 文件 | 说明 |
| :--- | :--- |
| `{apk_name}` | Android 安装包（纯 Java 原生） |
| `{ipa_name}` | iOS **未签名** IPA（纯 SwiftUI 原生） |
| `latest.json` | 端内「检查更新」清单 |
"""

with open("RELEASE_NOTES.md", "w", encoding="utf-8") as f:
    f.write(notes)
