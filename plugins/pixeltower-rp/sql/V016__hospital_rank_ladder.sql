-- ─── V016: San Francisco General Hospital rank ladder ────────────────────
-- Seeds the 23-rank ladder for corp_id=3 (hospital, V015):
--   Nurse I-V          (rank 1-5,   15c, VIEW_ROSTER)                     = 16
--   Doctor I-V         (rank 6-10,  17c, + BROADCAST)                     = 272
--   Surgeon I-V        (rank 11-15, 19c, same as Doctor)                  = 272
--   Paramedic I-V      (rank 16-20, 21c, same as Doctor)                  = 272
--   Supervisor         (rank 21,    23c, + HIRE/PROMOTE/DEMOTE
--                                          /VIEW_STOCK/VIEW_LEDGER)       = 381
--   Assistant Manager  (rank 22,    25c, + FIRE)                          = 383
--   General Manager    (rank 23,    27c, same as Assistant Manager)       = 383
--
-- Gating spec (per game design):
--   - HIRE / PROMOTE / DEMOTE reserved for Supervisor+ (ranks 21-23).
--   - FIRE reserved for Assistant Manager+ (ranks 22-23).
--   - ADJUST_SALARIES and WITHDRAW_STOCK are NOT granted to any hospital
--     rank — pay scales and stock outflows are policy, not employee-driven.
-- Doctor / Surgeon / Paramedic share bitmap 272; Assistant Manager and
-- General Manager share bitmap 383. Tiers within these groups are
-- differentiated by salary and title rather than authority.
--
-- Idempotent: hospital has no existing members at corp seed time, so no
-- pre-migration step is needed (cf. V003). ON DUPLICATE KEY UPDATE makes
-- re-runs safe after this file is edited.

INSERT INTO rp_corporation_ranks (corp_id, rank_num, title, salary, permissions) VALUES
    (3,  1, 'Nurse I',            15,   16),
    (3,  2, 'Nurse II',           15,   16),
    (3,  3, 'Nurse III',          15,   16),
    (3,  4, 'Nurse IV',           15,   16),
    (3,  5, 'Nurse V',            15,   16),
    (3,  6, 'Doctor I',           17,  272),
    (3,  7, 'Doctor II',          17,  272),
    (3,  8, 'Doctor III',         17,  272),
    (3,  9, 'Doctor IV',          17,  272),
    (3, 10, 'Doctor V',           17,  272),
    (3, 11, 'Surgeon I',          19,  272),
    (3, 12, 'Surgeon II',         19,  272),
    (3, 13, 'Surgeon III',        19,  272),
    (3, 14, 'Surgeon IV',         19,  272),
    (3, 15, 'Surgeon V',          19,  272),
    (3, 16, 'Paramedic I',        21,  272),
    (3, 17, 'Paramedic II',       21,  272),
    (3, 18, 'Paramedic III',      21,  272),
    (3, 19, 'Paramedic IV',       21,  272),
    (3, 20, 'Paramedic V',        21,  272),
    (3, 21, 'Supervisor',         23,  381),
    (3, 22, 'Assistant Manager',  25,  383),
    (3, 23, 'General Manager',    27,  383)
ON DUPLICATE KEY UPDATE
    title       = VALUES(title),
    salary      = VALUES(salary),
    permissions = VALUES(permissions);

-- Forward safety: prune any rank_num > 23 if a future migration narrows
-- the ladder. No-op on first apply.
DELETE FROM rp_corporation_ranks WHERE corp_id = 3 AND rank_num > 23;
