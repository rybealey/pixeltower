#!/usr/bin/env python3
"""
Apply pixeltower branding + key overrides to Nitro's runtime text files.

Reads gamedata-overrides/pixeltower-texts.json and rewrites
gamedata/gamedata/ExternalTexts.json + UITexts.json in place:

  1. Ordered string substitutions on every text value (longer match
     first, e.g. "Habbo Hotel" -> "PixelRP" before "Habbo" -> "Pixel").
     Values that look like URLs are skipped so .com / .net hostnames
     aren't mangled.
  2. Per-key overrides are upserted into both files so UITexts (loaded
     last by the client) wins the merge regardless of which file the
     key originally lived in.

Idempotent: re-running on already-substituted text is a no-op.

Usage:
  python3 scripts/apply-text-overrides.py
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "gamedata-overrides" / "pixeltower-texts.json"
TEXT_FILES = [
    ROOT / "gamedata" / "gamedata" / "ExternalTexts.json",
    ROOT / "gamedata" / "gamedata" / "UITexts.json",
]


def looks_like_url(value: str) -> bool:
    lowered = value.lower()
    if "://" in lowered or "http" in lowered:
        return True
    # Bare hostnames like "Habbo.com" / "HabboShire.net" that shouldn't
    # be rewritten into broken domains.
    for tld in (".com", ".net", ".org", ".io", ".fi", ".co.uk"):
        if tld in lowered:
            return True
    return False


def apply(data: dict, subs: list, overrides: dict) -> tuple[int, int]:
    sub_count = 0
    for key, value in list(data.items()):
        if not isinstance(value, str):
            continue
        if looks_like_url(value):
            continue
        new_value = value
        for src, dst in subs:
            if src in new_value:
                new_value = new_value.replace(src, dst)
        if new_value != value:
            data[key] = new_value
            sub_count += 1

    override_count = 0
    for key, value in overrides.items():
        if data.get(key) != value:
            data[key] = value
            override_count += 1

    return sub_count, override_count


def main() -> int:
    if not CONFIG_PATH.exists():
        print(f"[texts] no config at {CONFIG_PATH}, nothing to do")
        return 0

    with CONFIG_PATH.open(encoding="utf-8") as f:
        config = json.load(f)

    subs = [tuple(pair) for pair in config.get("substitutions", [])]
    overrides = config.get("overrides", {})

    touched_any = False
    for path in TEXT_FILES:
        if not path.exists():
            print(f"[texts] skip (missing): {path.relative_to(ROOT)}")
            continue

        with path.open(encoding="utf-8") as f:
            data = json.load(f)

        if not isinstance(data, dict):
            print(f"[texts] skip (not an object): {path.relative_to(ROOT)}")
            continue

        sub_count, override_count = apply(data, subs, overrides)

        with path.open("w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)

        touched_any = True
        print(
            f"[texts] {path.relative_to(ROOT)}: "
            f"{sub_count} substitutions, {override_count} overrides"
        )

    if not touched_any:
        print("[texts] no text files present yet — run after pull-default-pack.sh")
    return 0


if __name__ == "__main__":
    sys.exit(main())
