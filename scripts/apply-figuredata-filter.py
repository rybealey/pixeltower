#!/usr/bin/env python3
"""
Restrict the Nitro avatar editor to the canonical Habbo wardrobe.

Reads gamedata-overrides/habbo-standard-clothing.json (a set-ID whitelist
per settype, sourced once from habbo.com/gamedata/figuredata/1) and
rewrites gamedata/gamedata/FigureData.json in place:

  1. Sets whose id is in the whitelist stay selectable. If they carry
     a non-zero club level (HC-gated in canonical Habbo), the level is
     flattened to 0 so every signed-in user can wear them — same
     outcome the retired nitro-patches/unlock-clothing.patch used to
     achieve from the client side.
  2. Sets whose id is NOT in the whitelist are forced to selectable=false,
     which hides them from Change Your Looks but leaves the asset
     loadable so existing avatars still render correctly.
  3. Palette colors are flattened to club=0 defensively so HC palette
     entries (if upstream ever adds any) stay freely pickable.

Idempotent: re-running on an already-filtered file is a no-op.

Usage:
  python3 scripts/apply-figuredata-filter.py
"""

import json
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WHITELIST_PATH = ROOT / "gamedata-overrides" / "habbo-standard-clothing.json"
FIGUREDATA_PATH = ROOT / "gamedata" / "gamedata" / "FigureData.json"


def main() -> int:
    if not WHITELIST_PATH.exists():
        print(f"[figure] no whitelist at {WHITELIST_PATH}, nothing to do")
        return 0
    if not FIGUREDATA_PATH.exists():
        print(f"[figure] skip (missing): {FIGUREDATA_PATH.relative_to(ROOT)}")
        print("[figure]   run after pull-default-pack.sh / convert-swfs.sh")
        return 0

    with WHITELIST_PATH.open(encoding="utf-8") as f:
        whitelist_cfg = json.load(f)
    whitelist = {t: set(ids) for t, ids in whitelist_cfg.get("sets", {}).items()}

    with FIGUREDATA_PATH.open(encoding="utf-8") as f:
        data = json.load(f)

    kept = 0
    unhid = 0
    hidden = 0
    hc_flattened = 0
    for settype in data.get("setTypes", []):
        allowed = whitelist.get(settype.get("type"), set())
        for s in settype.get("sets", []):
            if s["id"] in allowed:
                kept += 1
                if not s.get("selectable", False):
                    s["selectable"] = True
                    unhid += 1
                if s.get("club", 0) != 0:
                    s["club"] = 0
                    hc_flattened += 1
            else:
                if s.get("selectable", False):
                    s["selectable"] = False
                    hidden += 1

    colors_flattened = 0
    for palette in data.get("palettes", []):
        for color in palette.get("colors", []):
            if color.get("club", 0) != 0:
                color["club"] = 0
                colors_flattened += 1

    # Write via tempfile + atomic rename so we don't need write permission on
    # the target file itself — only on its parent directory. The existing
    # FigureData.json may be owned by root (produced by a container) while
    # the deploy user only has directory-write access.
    fd, tmp_path = tempfile.mkstemp(
        prefix=".figuredata-", suffix=".json.tmp", dir=str(FIGUREDATA_PATH.parent)
    )
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        os.chmod(tmp_path, 0o644)
        os.replace(tmp_path, FIGUREDATA_PATH)
    except Exception:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)
        raise

    print(
        f"[figure] {FIGUREDATA_PATH.relative_to(ROOT)}: "
        f"{kept} whitelisted, {hidden} hidden, "
        f"{hc_flattened} HC flattened, {unhid} unhidden, "
        f"{colors_flattened} colors flattened"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
