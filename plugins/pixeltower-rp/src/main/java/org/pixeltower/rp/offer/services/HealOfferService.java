package org.pixeltower.rp.offer.services;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.InsufficientFundsException;
import org.pixeltower.rp.offer.OfferApplyException;
import org.pixeltower.rp.offer.OfferService;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;

import java.util.Optional;

/**
 * Hospital corp's healing service. Any hospital corp member, any rank,
 * may offer this — the corp affiliation is the only authorization. The
 * target pays {@code rp.offer.heal.price} coins (default 100) routed to
 * the hospital corp's treasury, and their HP is restored to maxHp.
 *
 * Design note: KO'd players (hp == 0) are deliberately not healable via
 * this command — downed players still need staff {@code :restore} or
 * the respawn timer. This keeps the medical-respawn loop intact.
 */
public class HealOfferService implements OfferService {

    private static final String CORP_KEY = "hospital";
    private static final String PRICE_CONFIG_KEY = "rp.offer.heal.price";
    private static final long DEFAULT_PRICE = 100L;
    private static final String LEDGER_REASON = "offer_heal";

    @Override
    public String key() {
        return "heal";
    }

    @Override
    public int corpId() {
        return CorporationManager.getByKey(CORP_KEY)
                .map(corp -> corp.getId())
                .orElse(-1);
    }

    @Override
    public String title() {
        return "Healing Service";
    }

    @Override
    public String description() {
        return "Restore your HP to full.";
    }

    @Override
    public String iconResource() {
        return "";
    }

    @Override
    public long price() {
        String raw = Emulator.getConfig().getValue(PRICE_CONFIG_KEY,
                String.valueOf(DEFAULT_PRICE));
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return DEFAULT_PRICE;
        }
    }

    @Override
    public Optional<String> validateOfferer(Habbo caller) {
        int hospitalCorpId = corpId();
        if (hospitalCorpId < 0) {
            return Optional.of("Hospital corporation is not configured.");
        }
        boolean inHospital = CorporationManager.getMembership(caller.getHabboInfo().getId())
                .map(m -> m.getCorpId() == hospitalCorpId)
                .orElse(false);
        if (!inHospital) {
            return Optional.of("Only hospital staff can offer healing.");
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> validateTarget(Habbo caller, ResolvedTarget target) {
        if (!target.isOnline()) {
            return Optional.of(target.username + " is not online.");
        }
        Room callerRoom = caller.getHabboInfo().getCurrentRoom();
        Room targetRoom = target.online.getHabboInfo().getCurrentRoom();
        if (callerRoom == null || targetRoom == null
                || callerRoom.getId() != targetRoom.getId()) {
            return Optional.of(target.username + " must be in the same room as you.");
        }
        PlayerStats stats = StatsManager.get(target.habboId).orElse(null);
        if (stats == null) {
            return Optional.of(target.username + " has no stats record yet.");
        }
        if (stats.getHp() <= 0) {
            return Optional.of(target.username + " is downed and needs a respawn.");
        }
        if (stats.getHp() >= stats.getMaxHp()) {
            return Optional.of(target.username + " is already at full health.");
        }
        return Optional.empty();
    }

    @Override
    public void apply(int offererId, Habbo target) throws OfferApplyException {
        int targetId = target.getHabboInfo().getId();
        String targetName = target.getHabboInfo().getUsername();
        PlayerStats stats = StatsManager.get(targetId).orElse(null);
        if (stats == null) {
            throw new OfferApplyException(targetName + " has no stats record.");
        }
        if (stats.getHp() <= 0) {
            throw new OfferApplyException(targetName + " is downed and can't be healed.");
        }
        int missing = stats.getMaxHp() - stats.getHp();
        if (missing <= 0) {
            throw new OfferApplyException(targetName + " is already at full health.");
        }
        int hospitalCorpId = corpId();
        if (hospitalCorpId < 0) {
            throw new OfferApplyException("Hospital corporation is not configured.");
        }

        try {
            BankManager.chargePlayerToTreasury(target, hospitalCorpId, price(), LEDGER_REASON);
        } catch (InsufficientFundsException e) {
            throw new OfferApplyException(targetName
                    + " can't afford the heal (needs " + price() + " coins).");
        }

        StatsManager.adjustHp(targetId, missing);
        StatsManager.get(targetId).ifPresent(updated ->
                target.getClient().sendResponse(new UpdatePlayerStatsComposer(updated)));
    }
}
