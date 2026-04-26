#!/usr/bin/env python3
"""
Regenerate gamedata/gamedata/FigureData.json from gamedata/gamedata/figuredata.xml.

Mirrors the logic in docker/converter/app/src/common/mapping/mappers/FigureDataMapper.ts
so the output matches what the Nitro converter would produce — useful when you
want the JSON to reflect a fresh figuredata.xml without running the full
nitro-converter container.

Usage:
  python3 scripts/regen-figuredata-from-xml.py
"""

import json
import os
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
XML_PATH = ROOT / "gamedata" / "gamedata" / "figuredata.xml"
JSON_PATH = ROOT / "gamedata" / "gamedata" / "FigureData.json"


# Defaults match the TS converter's XML wrapper classes — a missing or
# unparseable numeric attr becomes 0, a missing boolean becomes false,
# a missing string becomes "". This is what
# docker/converter/app/src/common/mapping/xml/figuredata/*.ts does.

def i(s, default=0):
    try:
        return int(s) if s is not None else default
    except ValueError:
        return default


def b(s):
    return s == "1"


def main() -> int:
    if not XML_PATH.exists():
        print(f"[error] {XML_PATH} not found", file=sys.stderr)
        return 1

    tree = ET.parse(XML_PATH)
    root = tree.getroot()

    out = {"palettes": [], "setTypes": []}

    for palette_xml in root.findall("colors/palette"):
        palette = {"id": i(palette_xml.get("id")), "colors": []}
        for color_xml in palette_xml.findall("color"):
            palette["colors"].append({
                "id": i(color_xml.get("id")),
                "index": i(color_xml.get("index")),
                "club": i(color_xml.get("club")),
                "selectable": b(color_xml.get("selectable")),
                "hexCode": (color_xml.text or "").strip(),
            })
        out["palettes"].append(palette)

    for settype_xml in root.findall("sets/settype"):
        settype = {
            "type": settype_xml.get("type") or "",
            "paletteId": i(settype_xml.get("paletteid"), default=1),
            "mandatory_f_0": b(settype_xml.get("mand_f_0")),
            "mandatory_f_1": b(settype_xml.get("mand_f_1")),
            "mandatory_m_0": b(settype_xml.get("mand_m_0")),
            "mandatory_m_1": b(settype_xml.get("mand_m_1")),
            "sets": [],
        }
        for set_xml in settype_xml.findall("set"):
            s = {
                "id": i(set_xml.get("id")),
                "gender": set_xml.get("gender") or "",
                "club": i(set_xml.get("club")),
                "colorable": b(set_xml.get("colorable")),
                "selectable": b(set_xml.get("selectable")),
                "preselectable": b(set_xml.get("preselectable")),
                "sellable": b(set_xml.get("sellable")),
                "parts": [],
            }
            for part_xml in set_xml.findall("part"):
                s["parts"].append({
                    "id": i(part_xml.get("id")),
                    "type": part_xml.get("type") or "",
                    "colorable": b(part_xml.get("colorable")),
                    "index": i(part_xml.get("index")),
                    "colorindex": i(part_xml.get("colorindex")),
                })
            hidden = [{"partType": l.get("parttype") or ""} for l in set_xml.findall("hiddenlayers/layer")]
            if hidden:
                s["hiddenLayers"] = hidden
            settype["sets"].append(s)
        out["setTypes"].append(settype)

    new_body = json.dumps(out, ensure_ascii=False).encode("utf-8")

    # Skip write if content is byte-identical — preserves mtime so nginx
    # ETag/Last-Modified stay stable and don't bust client caches on every
    # deploy (this script runs on every deploy via scripts/deploy.sh).
    if JSON_PATH.exists() and JSON_PATH.read_bytes() == new_body:
        print(f"[regen] {JSON_PATH.relative_to(ROOT)}: unchanged, skipping write")
        return 0

    # Atomic write — same pattern as the old apply-figuredata-filter.py used,
    # since FigureData.json may be owned by root from prior converter runs.
    fd, tmp = tempfile.mkstemp(prefix=".figuredata-", suffix=".json.tmp", dir=str(JSON_PATH.parent))
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(new_body)
        os.chmod(tmp, 0o644)
        os.replace(tmp, JSON_PATH)
    except Exception:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise

    selectable_sets = sum(1 for st in out["setTypes"] for s in st["sets"] if s.get("selectable"))
    total_sets = sum(len(st["sets"]) for st in out["setTypes"])
    print(f"[regen] {JSON_PATH.relative_to(ROOT)}: "
          f"{len(out['palettes'])} palettes, "
          f"{len(out['setTypes'])} settypes, "
          f"{total_sets} sets ({selectable_sets} selectable)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
