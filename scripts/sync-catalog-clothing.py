#!/usr/bin/env python3
"""
Wire catalog clothing → wardrobe unlock end-to-end.

Two coupled mutations, both idempotent. Run on every deploy after the
gamedata regen, requires the db container to be up.

(1) catalog_clothing.setid translation.
    The Morningstar pack ships catalog_clothing with PART ids in the setid
    column (e.g. clothing_squid → "3356"). Nitro's wardrobe gate checks
    the user's unlock list against figuredata SET ids — the legacy
    Morningstar pack wraps part 3356 inside set#990000106. So the IDs the
    server sends in the FigureSetIds packet never match. Translate every
    catalog_clothing.setid value from PART id to the SET id(s) that
    contain it. Skip values that are already SET ids (already migrated)
    or unknown (no figuredata coverage).

(2) figuredata.sellable flip.
    Nitro's gating is gated on `partSet.isSellable` — only sellable sets
    are checked against the user's unlock list. The Morningstar pack ships
    every set with sellable=false. Mark every set referenced by
    catalog_clothing.setid (after translation) as sellable=true so the
    gate actually runs.

Without (1) the IDs don't match. Without (2) the gate is never even
evaluated. Both are required for catalog purchases to translate to
wardrobe unlocks.
"""

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIGUREDATA_PATH = ROOT / "gamedata" / "gamedata" / "FigureData.json"
DC = os.environ.get("DC", "docker compose").split()
ENV_FILE = os.environ.get("ENV_FILE", ".env")


def db_query(sql: str) -> str:
    """Run a SELECT and return raw mariadb -N -B output (TSV, no header)."""
    cmd = DC + ["--env-file", ENV_FILE, "exec", "-T", "db", "sh", "-c",
               f'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE" -N -B -e {shell_quote(sql)}']
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


# Set to True by db_apply so we know whether to restart the emulator at the
# end (Arcturus caches catalog_pages / catalog_items at boot — DB writes are
# invisible to clients until the emulator process reloads).
_db_dirty = False


def db_apply(sql: str) -> None:
    """Pipe SQL into mariadb (UPDATEs, multi-statement)."""
    global _db_dirty
    _db_dirty = True
    cmd = DC + ["--env-file", ENV_FILE, "exec", "-T", "db", "sh", "-c",
               'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"']
    subprocess.run(cmd, input=sql, check=True, text=True)


def shell_quote(s: str) -> str:
    return "'" + s.replace("'", "'\\''") + "'"


def main() -> int:
    if not FIGUREDATA_PATH.exists():
        print(f"[catalog-sync] {FIGUREDATA_PATH} missing, skipping", file=sys.stderr)
        return 0

    with FIGUREDATA_PATH.open(encoding="utf-8") as f:
        fd = json.load(f)

    # part_id → set of set_ids that have it as a primary part (matches settype.type).
    # In the Morningstar pack a part can show up in multiple sets (color/index variants).
    part_to_sets: dict[int, set[int]] = {}
    all_set_ids: set[int] = set()
    for st in fd.get("setTypes", []):
        t = st["type"]
        for s in st.get("sets", []):
            sid = s["id"]
            all_set_ids.add(sid)
            for p in s.get("parts", []):
                if p.get("type") == t:
                    part_to_sets.setdefault(p["id"], set()).add(sid)

    # Read catalog_clothing.
    try:
        rows = db_query("SELECT id, name, setid FROM catalog_clothing").splitlines()
    except subprocess.CalledProcessError as e:
        print(f"[catalog-sync] DB read failed: {e}", file=sys.stderr)
        return 1

    catalog: list[tuple[int, str, str]] = []
    for line in rows:
        if not line: continue
        parts = line.split("\t")
        if len(parts) < 3: continue
        catalog.append((int(parts[0]), parts[1], parts[2]))

    update_sql = []
    translated = unchanged = unknown_total = 0
    catalog_set_ids: set[int] = set()
    # Names of catalog_clothing rows that have at least one figuredata SET id
    # in their (post-translation) setid — i.e. clothing that will actually
    # gate correctly through the wardrobe pipeline. Used downstream to route
    # the matching catalog_items into the staff Clothing page.
    wired_names: set[str] = set()
    for cid, name, setid_csv in catalog:
        old_values = []
        new_values: list[int] = []
        unknown = []
        for x in setid_csv.split(","):
            x = x.strip()
            if not x.lstrip("-").isdigit():
                continue
            v = int(x)
            old_values.append(v)
            if v in all_set_ids:
                # Already a known SET id (either a SET, or a part_id that happens
                # to coincide with a set_id — the latter is rare; preserve as-is).
                new_values.append(v)
            elif v in part_to_sets:
                new_values.extend(sorted(part_to_sets[v]))
            else:
                unknown.append(v)
                # Preserve unknown values verbatim — figuredata gap, not our place
                # to silently drop.
                new_values.append(v)
        new_csv = ",".join(str(x) for x in sorted(set(new_values)))
        catalog_set_ids.update(v for v in new_values if v in all_set_ids)
        if any(v in all_set_ids for v in new_values):
            wired_names.add(name)
        if unknown:
            unknown_total += len(unknown)
        if new_csv != setid_csv:
            translated += 1
            esc_csv = new_csv.replace("'", "''")
            update_sql.append(f"UPDATE catalog_clothing SET setid='{esc_csv}' WHERE id={cid};")
        else:
            unchanged += 1

    if update_sql:
        # Original column is VARCHAR(75) — fine for short PART ids (3356) but
        # too narrow once we expand to figuredata SET ids (990000257 etc.) and
        # several-set bundles (clothing_oxset, clothing_goldhatpack1, ...).
        # MODIFY is a no-op once already at this width, so cheap to re-run.
        db_apply("ALTER TABLE catalog_clothing MODIFY COLUMN setid VARCHAR(512) NOT NULL;\n")
        db_apply("\n".join(update_sql) + "\n")
    print(f"[catalog-sync] catalog_clothing: {translated} rows translated, "
          f"{unchanged} unchanged, {unknown_total} unknown part-ids preserved")

    # Mark figuredata sets sellable=true if they're referenced by any catalog_clothing row.
    flipped = 0
    for st in fd.get("setTypes", []):
        for s in st.get("sets", []):
            should_be_sellable = s["id"] in catalog_set_ids
            if should_be_sellable and not s.get("sellable"):
                s["sellable"] = True
                flipped += 1

    new_body = json.dumps(fd, ensure_ascii=False).encode("utf-8")
    if FIGUREDATA_PATH.read_bytes() == new_body:
        print(f"[catalog-sync] figuredata: no changes ({len(catalog_set_ids)} sets already sellable)")
    else:
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
        print(f"[catalog-sync] figuredata: marked {flipped} sets sellable=true "
              f"({len(catalog_set_ids)} catalog-controlled sets total)")

    organize_staff_clothing_page(wired_names)

    if _db_dirty:
        print("[catalog-sync] restarting emulator to reload catalog cache")
        subprocess.run(DC + ["--env-file", ENV_FILE, "restart", "emulator"], check=False)
    return 0


# Hardcoded staff parent. Created manually in catalog_pages and stable.
STAFF_PIXELRP_PAGE_ID = 9001
CLOTHING_PAGE_CAPTION_SAVE = "pixelrp_clothing"


def organize_staff_clothing_page(wired_names: set[str]) -> None:
    """Group every wired clothing catalog_item under Staff › PixelRP › Clothing.

    Idempotent: creates the page if missing, and only UPDATEs catalog_items
    whose page_id isn't already the target.
    """
    if not wired_names:
        print("[catalog-sync] no wired clothing names — skipping staff page organize")
        return

    existing = db_query(
        f"SELECT id FROM catalog_pages WHERE parent_id={STAFF_PIXELRP_PAGE_ID} "
        f"AND caption_save='{CLOTHING_PAGE_CAPTION_SAVE}'"
    ).strip()
    if existing:
        page_id = int(existing.splitlines()[0])
    else:
        # Match the styling of sibling staff pages (icon_image=65, min_rank=4).
        # Auto-increment id; we re-SELECT after insert to capture it.
        db_apply(
            "INSERT INTO catalog_pages "
            "(parent_id, caption_save, caption, page_layout, icon_color, icon_image, "
            " min_rank, order_num, visible, enabled, club_only, vip_only, "
            " page_headline, page_teaser, includes) VALUES "
            f"({STAFF_PIXELRP_PAGE_ID}, '{CLOTHING_PAGE_CAPTION_SAVE}', 'Clothing', "
            f"'default_3x3', 1, 65, 4, 2, '1', '1', '0', '0', '', '', '');\n"
        )
        page_id = int(db_query(
            f"SELECT id FROM catalog_pages WHERE parent_id={STAFF_PIXELRP_PAGE_ID} "
            f"AND caption_save='{CLOTHING_PAGE_CAPTION_SAVE}'"
        ).strip().splitlines()[0])
        print(f"[catalog-sync] created Staff › PixelRP › Clothing page (id={page_id})")

    # Build the IN (...) list of wired names. catalog_clothing.name shouldn't
    # contain quotes (it's an item_name), but escape defensively.
    quoted = ",".join("'" + n.replace("'", "''") + "'" for n in sorted(wired_names))
    moved_count_raw = db_query(
        f"SELECT COUNT(*) FROM catalog_items "
        f"WHERE catalog_name IN ({quoted}) AND page_id != {page_id}"
    ).strip()
    pending = int(moved_count_raw) if moved_count_raw.isdigit() else 0
    if pending == 0:
        print(f"[catalog-sync] staff Clothing page (id={page_id}): all {len(wired_names)} wired items already routed")
        return

    db_apply(
        f"UPDATE catalog_items SET page_id={page_id} "
        f"WHERE catalog_name IN ({quoted}) AND page_id != {page_id};\n"
    )
    print(f"[catalog-sync] moved {pending} wired clothing catalog_items into staff Clothing page (id={page_id})")


if __name__ == "__main__":
    sys.exit(main())
