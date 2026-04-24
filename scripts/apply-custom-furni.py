#!/usr/bin/env python3
"""
Sync tracked custom furniture into gamedata/ and patch FurnitureData.json.

Walks custom-furni/<classname>/ directories — each one contains:
  - item.nitro     Nitro bundle (pre-converted locally via convert-swfs.sh)
  - icon.png       Catalog icon
  - metadata.json  One FurnitureData.json entry (id, classname, dims, etc.)
  - item.swf       (optional) Raw source, useful for re-conversion

For each entry:
  1. Copies item.nitro → gamedata/bundled/furniture/<classname>.nitro
  2. Copies icon.png  → gamedata/c_images/catalogue/<classname>_icon.png
  3. Upserts metadata.json into gamedata/gamedata/FurnitureData.json
     (matched by classname; replaces the existing entry if one is there,
     otherwise appends).

Idempotent — re-running on an already-synced tree is a near-zero op.
Writes FurnitureData.json via tempfile + atomic rename so it works even
when the target file is root-owned (same pattern apply-figuredata-filter
uses).

Usage:
  python3 scripts/apply-custom-furni.py
"""

import json
import os
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CUSTOM_DIR = ROOT / "custom-furni"
BUNDLES_DIR = ROOT / "gamedata" / "bundled" / "furniture"
ICONS_DIR = ROOT / "gamedata" / "c_images" / "catalogue"
FURNIDATA = ROOT / "gamedata" / "gamedata" / "FurnitureData.json"


def atomic_write_json(path: Path, data) -> None:
    fd, tmp = tempfile.mkstemp(prefix=".furnidata-", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        os.chmod(tmp, 0o644)
        os.replace(tmp, path)
    except Exception:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise


def main() -> int:
    if not CUSTOM_DIR.is_dir():
        print(f"[furni] no {CUSTOM_DIR.relative_to(ROOT)}, nothing to do")
        return 0

    items = [p for p in CUSTOM_DIR.iterdir() if p.is_dir()]
    if not items:
        print(f"[furni] {CUSTOM_DIR.relative_to(ROOT)} is empty")
        return 0

    BUNDLES_DIR.mkdir(parents=True, exist_ok=True)
    ICONS_DIR.mkdir(parents=True, exist_ok=True)

    if FURNIDATA.exists():
        with FURNIDATA.open(encoding="utf-8") as f:
            fd = json.load(f)
    else:
        print(f"[furni] skip (missing): {FURNIDATA.relative_to(ROOT)}")
        return 0

    entries = fd["roomitemtypes"]["furnitype"]
    by_classname = {e.get("classname"): i for i, e in enumerate(entries)}

    synced = 0
    patched = 0
    for d in sorted(items):
        classname = d.name
        nitro = d / "item.nitro"
        icon = d / "icon.png"
        meta = d / "metadata.json"

        if nitro.is_file():
            shutil.copy2(nitro, BUNDLES_DIR / f"{classname}.nitro")
        if icon.is_file():
            shutil.copy2(icon, ICONS_DIR / f"{classname}_icon.png")
        if meta.is_file():
            with meta.open(encoding="utf-8") as f:
                entry = json.load(f)
            entry["classname"] = classname  # directory name is authoritative
            idx = by_classname.get(classname)
            if idx is None:
                entries.append(entry)
                by_classname[classname] = len(entries) - 1
                patched += 1
            elif entries[idx] != entry:
                entries[idx] = entry
                patched += 1
        synced += 1

    if patched:
        atomic_write_json(FURNIDATA, fd)

    print(
        f"[furni] {synced} item(s) synced from {CUSTOM_DIR.relative_to(ROOT)}/, "
        f"{patched} FurnitureData.json entry change(s)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
