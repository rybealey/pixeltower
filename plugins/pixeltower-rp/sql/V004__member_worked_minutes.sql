-- ─── V004: persist per-member paycheck progress ──────────────────────────
-- Adds a counter column that ShiftManager write-through updates on every
-- shift tick, :stopwork, and disconnect. Range: 0 … (PAY_EVERY_MINUTES-1).
-- Survives logouts and emulator restarts so a player who works 5 minutes
-- and logs off resumes at minute 5 on their next :startwork.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS is a no-op on re-run. New hires
-- default to 0 via the column default.

ALTER TABLE rp_corporation_members
    ADD COLUMN IF NOT EXISTS `worked_minutes_in_cycle` INT NOT NULL DEFAULT 0;
