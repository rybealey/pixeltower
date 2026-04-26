#!/usr/bin/env python3
"""
Hide wardrobe sets that have no renderable assets.

A set is `selectable=true` in figuredata.xml but its backing .nitro library
isn't present in gamedata/bundled/figure/ — usually because the source SWF
pack didn't include that library. Without this pass, the wardrobe shows
blank tiles for those sets.

A set is "renderable" if at least ONE of its parts maps (via FigureMap.json)
to a library that exists on disk. Sets with no renderable parts get
`selectable=false`. Sets that just have some variant indices missing are
left alone — they render correctly in the primary pose.

Idempotent: only writes if the file changed. Wired into scripts/deploy.sh
after the XML→JSON regen so prod always reflects the current asset state.

Usage:
  python3 scripts/hide-unrenderable-sets.py
"""

import json
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIGUREDATA_PATH = ROOT / "gamedata" / "gamedata" / "FigureData.json"
FIGUREMAP_PATH = ROOT / "gamedata" / "gamedata" / "FigureMap.json"
NITRO_DIR = ROOT / "gamedata" / "bundled" / "figure"


def main() -> int:
    if not FIGUREDATA_PATH.exists() or not FIGUREMAP_PATH.exists():
        print(f"[unrenderable] missing figuredata or figuremap, skipping")
        return 0
    if not NITRO_DIR.is_dir():
        print(f"[unrenderable] {NITRO_DIR} missing, skipping")
        return 0

    with FIGUREDATA_PATH.open(encoding="utf-8") as f:
        fd = json.load(f)
    with FIGUREMAP_PATH.open(encoding="utf-8") as f:
        fm = json.load(f)

    part_to_lib = {
        (p["type"], p["id"]): lib["id"]
        for lib in fm.get("libraries", [])
        for p in lib.get("parts", [])
    }
    existing = {
        f[: -len(".nitro")]
        for f in os.listdir(NITRO_DIR)
        if f.endswith(".nitro")
    }

    hidden = 0
    for st in fd.get("setTypes", []):
        for s in st.get("sets", []):
            if not s.get("selectable"):
                continue
            parts = s.get("parts", [])
            if not parts:
                continue
            renderable = any(
                part_to_lib.get((p["type"], p["id"])) in existing
                for p in parts
            )
            if not renderable:
                s["selectable"] = False
                hidden += 1

    new_body = json.dumps(fd, ensure_ascii=False).encode("utf-8")

    if FIGUREDATA_PATH.read_bytes() == new_body:
        print(f"[unrenderable] no changes ({hidden} would-be-hidden already in current file)")
        return 0

    fd_, tmp = tempfile.mkstemp(prefix=".figuredata-", suffix=".json.tmp", dir=str(FIGUREDATA_PATH.parent))
    try:
        with os.fdopen(fd_, "wb") as f:
            f.write(new_body)
        os.chmod(tmp, 0o644)
        os.replace(tmp, FIGUREDATA_PATH)
    except Exception:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise

    print(f"[unrenderable] hid {hidden} sets with no backing .nitro library")
    return 0


if __name__ == "__main__":
    sys.exit(main())
