package org.pixeltower.rp.corp.tasks;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.corp.Corporation;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.CorporationMember;
import org.pixeltower.rp.corp.CorporationRank;
import org.pixeltower.rp.corp.ShiftManager;
import org.pixeltower.rp.core.outgoing.PlaySoundComposer;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.MoneyLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Per-minute shift heartbeat. Only touches players who are clocked in via
 * {@code :startwork} (tracked in {@link ShiftManager}):
 *   - Increments their per-cycle minute counter.
 *   - If the counter hits {@link ShiftManager#PAY_EVERY_MINUTES}, credits
 *     the rank's salary and resets to 0.
 *   - Otherwise, whispers the countdown to the next paycheck.
 *
 * Designed to be scheduled every 60 seconds. Offline / vanished players
 * are auto-clocked-out defensively (the {@code UserDisconnectEvent}
 * handler in {@code PixeltowerRP} is the primary cleanup path).
 */
public class PaycheckTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaycheckTask.class);

    @Override
    public void run() {
        int paychecksFired = 0;
        long paidTotal = 0;

        for (Map.Entry<Integer, Integer> entry : ShiftManager.shifts()) {
            int habboId = entry.getKey();

            Habbo online = Emulator.getGameEnvironment()
                    .getHabboManager().getHabbo(habboId);
            if (online == null) {
                ShiftManager.stopWork(habboId);
                continue;
            }

            // Auto-clockout on passive idle (zzz). Arcturus detects this via
            // RoomUnit.idleTimer but never fires UserIdleEvent for the timer
            // path — so we poll each heartbeat.
            if (online.getRoomUnit() != null && online.getRoomUnit().isIdle()) {
                if (online.getRoomUnit().isInRoom()) {
                    online.shout("*has stopped working as they've fallen asleep.*",
                            RoomChatMessageBubbles.BLACK);
                }
                ShiftManager.stopWork(habboId);
                continue;
            }

            CorporationMember member = CorporationManager.getMembership(habboId).orElse(null);
            if (member == null) {
                ShiftManager.stopWork(habboId);
                continue;
            }
            Corporation corp = CorporationManager.getById(member.getCorpId()).orElse(null);
            if (corp == null) {
                ShiftManager.stopWork(habboId);
                continue;
            }
            CorporationRank rank = corp.getRanks().get(member.getRankNum());

            int newMinutes = ShiftManager.incrementAndGet(habboId);
            if (newMinutes < 0) continue;  // player clocked out between iteration and increment
            if (newMinutes >= ShiftManager.PAY_EVERY_MINUTES) {
                long salary = rank != null ? rank.getSalary() : 0L;
                if (salary > 0) {
                    Long refId = (long) corp.getId();
                    try {
                        if (BankManager.hasAccount(habboId)) {
                            BankManager.adminAdjust(habboId, salary, "paycheck_bank", refId);
                            online.getClient().sendResponse(
                                    new PlaySoundComposer(PlaySoundComposer.SAMPLE_CREDITS));
                            LOGGER.info("paycheck_bank sound-packet sent habbo={} corp={} sample={}",
                                    habboId, corp.getId(), PlaySoundComposer.SAMPLE_CREDITS);
                            online.whisper("A paycheck of $" + salary
                                            + " has been direct deposited into your account.",
                                    RoomChatMessageBubbles.WIRED);
                        } else {
                            MoneyLedger.credit(online, salary, "paycheck_cash", refId);
                            online.whisper("You've been paid $" + salary + " in cash.",
                                    RoomChatMessageBubbles.WIRED);
                        }
                        paychecksFired++;
                        paidTotal += salary;
                    } catch (Exception ex) {
                        LOGGER.error("paycheck credit failed habbo={} corp={} salary={}",
                                habboId, corp.getId(), salary, ex);
                    }
                }
                ShiftManager.resetMinutes(habboId);
            } else {
                int remaining = ShiftManager.PAY_EVERY_MINUTES - newMinutes;
                String pluralized = remaining == 1 ? "minute" : "minutes";
                online.whisper("You'll receive your next paycheck in "
                                + remaining + " " + pluralized + ".",
                        RoomChatMessageBubbles.WIRED);
            }
        }

        if (paychecksFired > 0) {
            LOGGER.info("paycheck_tick paychecks_fired={} total_paid={}",
                    paychecksFired, paidTotal);
        }
    }
}
