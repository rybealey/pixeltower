-- ─── V003: Police Department rank restructure ─────────────────────────────
-- Replaces the V001 5-rank ladder (Cadet / Officer / Sergeant / Lieutenant /
-- Chief of Police) with the canonical 18-rank ladder:
--   Private I-V (rank 1-5,   $10, VIEW_ROSTER)
--   Corporal I-V (rank 6-10, $12, + BROADCAST)
--   Sergeant I-V (rank 11-15,$14, + HIRE/FIRE)
--   Lieutenant   (rank 16,   $16, + PROMOTE/DEMOTE/VIEW_STOCK/VIEW_LEDGER)
--   Captain      (rank 17,   $18, + ADJUST_SALARIES)
--   Colonel      (rank 18,   $20, all bits = 1023)
--
-- Idempotent: applying to the V001 seed migrates existing members to their
-- new equivalents (Chief→Colonel, Lieutenant→Lieutenant, Sergeant→Sergeant I,
-- Officer→Corporal I, Cadet→Private I); applying to already-migrated state
-- is a no-op.

-- ─── Step 1: migrate existing members from V001 ranks to new equivalents ──
-- Title-guarded joins only match rows whose rank *still bears the old name*;
-- after Step 2 rewrites the rank table, these UPDATEs become no-ops on re-run.

UPDATE rp_corporation_members m
  JOIN rp_corporation_ranks r
    ON r.corp_id = m.corp_id AND r.rank_num = m.rank_num
SET m.rank_num = 18
WHERE m.corp_id = 1 AND m.rank_num = 5 AND r.title = 'Chief of Police';

UPDATE rp_corporation_members m
  JOIN rp_corporation_ranks r
    ON r.corp_id = m.corp_id AND r.rank_num = m.rank_num
SET m.rank_num = 16
WHERE m.corp_id = 1 AND m.rank_num = 4 AND r.title = 'Lieutenant' AND r.salary = 400;

UPDATE rp_corporation_members m
  JOIN rp_corporation_ranks r
    ON r.corp_id = m.corp_id AND r.rank_num = m.rank_num
SET m.rank_num = 11
WHERE m.corp_id = 1 AND m.rank_num = 3 AND r.title = 'Sergeant';

UPDATE rp_corporation_members m
  JOIN rp_corporation_ranks r
    ON r.corp_id = m.corp_id AND r.rank_num = m.rank_num
SET m.rank_num = 6
WHERE m.corp_id = 1 AND m.rank_num = 2 AND r.title = 'Officer';

-- Cadet (rank 1, V001) → Private I (rank 1, V003): rank_num unchanged.

-- ─── Step 2: upsert the 18-rank ladder ────────────────────────────────────
INSERT INTO rp_corporation_ranks (corp_id, rank_num, title, salary, permissions) VALUES
    (1,  1, 'Private I',    10, 16),
    (1,  2, 'Private II',   10, 16),
    (1,  3, 'Private III',  10, 16),
    (1,  4, 'Private IV',   10, 16),
    (1,  5, 'Private V',    10, 16),
    (1,  6, 'Corporal I',   12, 272),
    (1,  7, 'Corporal II',  12, 272),
    (1,  8, 'Corporal III', 12, 272),
    (1,  9, 'Corporal IV',  12, 272),
    (1, 10, 'Corporal V',   12, 272),
    (1, 11, 'Sergeant I',   14, 275),
    (1, 12, 'Sergeant II',  14, 275),
    (1, 13, 'Sergeant III', 14, 275),
    (1, 14, 'Sergeant IV',  14, 275),
    (1, 15, 'Sergeant V',   14, 275),
    (1, 16, 'Lieutenant',   16, 383),
    (1, 17, 'Captain',      18, 511),
    (1, 18, 'Colonel',      20, 1023)
ON DUPLICATE KEY UPDATE
    title       = VALUES(title),
    salary      = VALUES(salary),
    permissions = VALUES(permissions);

-- ─── Step 3: prune any ranks beyond the new ladder ────────────────────────
-- No-op in the V001→V003 transition (old ladder had rank_num 1..5 only);
-- kept for forward safety if a future migration narrows the ladder.
DELETE FROM rp_corporation_ranks WHERE corp_id = 1 AND rank_num > 18;
