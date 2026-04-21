package org.pixeltower.rp;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import org.pixeltower.rp.economy.commands.AwardCommand;
import org.pixeltower.rp.economy.commands.BalanceCommand;
import org.pixeltower.rp.economy.commands.BankCommand;
import org.pixeltower.rp.economy.commands.DepositCommand;
import org.pixeltower.rp.economy.commands.GiveCommand;
import org.pixeltower.rp.economy.commands.OpenAccountCommand;
import org.pixeltower.rp.economy.commands.TransferCommand;
import org.pixeltower.rp.economy.commands.WithdrawCommand;
import org.pixeltower.rp.economy.tasks.BankInterestTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Pixeltower RP plugin entrypoint. Lifecycle is driven by Arcturus:
 *   onEnable()  — class loaded, event listeners registered.
 *   onEmulatorLoadedEvent() — everything else (DB managers, config, room
 *                              manager) is ready; safe to read/write state.
 *   onDisable() — graceful shutdown.
 *
 * Future tiers' subsystems (stats, economy, corps, fight, medical, police)
 * will be wired in from onEmulatorLoadedEvent so they can rely on a fully
 * initialized Emulator.
 */
public class PixeltowerRP extends HabboPlugin implements EventListener {

    public static final String VERSION = "0.1.0";
    private static final Logger LOGGER = LoggerFactory.getLogger(PixeltowerRP.class);

    @Override
    public void onEnable() throws Exception {
        Emulator.getPluginManager().registerEvents(this, this);
        LOGGER.info("Pixeltower RP v{} — onEnable", VERSION);

        // If the emulator is already fully loaded when this plugin enables
        // (e.g. hot-swap via reload), kick the post-load hook manually.
        if (Emulator.isReady && !Emulator.isShuttingDown) {
            this.onEmulatorLoadedEvent(null);
        }
    }

    @Override
    public void onDisable() throws Exception {
        LOGGER.info("Pixeltower RP v{} — onDisable", VERSION);
    }

    @Override
    public boolean hasPermission(Habbo habbo, String s) {
        return false;
    }

    @EventHandler
    public void onEmulatorLoadedEvent(EmulatorLoadedEvent event) {
        // Register the rp.* config keys so emulator_settings rows populate
        // with defaults on first boot. Per-subsystem managers will read them
        // on init in later Tier 1 commits.
        Emulator.getConfig().register("rp.corp.paycheck_tick_s",  "600");
        Emulator.getConfig().register("rp.stats.hp_regen_interval_s", "30");
        Emulator.getConfig().register("rp.stats.hp_regen_amount",     "2");
        Emulator.getConfig().register("rp.stats.default_hp",          "100");
        Emulator.getConfig().register("rp.stats.default_energy",      "100");
        Emulator.getConfig().register("rp.fight.hit_window_ms",       "500");
        Emulator.getConfig().register("rp.fight.fade_window_ms",      "500");
        Emulator.getConfig().register("rp.fight.energy_per_hit",      "10");
        Emulator.getConfig().register("rp.fight.damage_variance",     "0.2");
        Emulator.getConfig().register("rp.medical.respawn_timeout_s", "180");
        Emulator.getConfig().register("rp.medical.hospital_room_id",  "0");
        Emulator.getConfig().register("rp.police.jail_room_id",       "0");

        Emulator.getConfig().register("rp.bank.fee_rate",             "0.01");
        Emulator.getConfig().register("rp.bank.fee_corp_key",         "bank");
        Emulator.getConfig().register("rp.bank.interest_rate",        "0.001");
        Emulator.getConfig().register("rp.bank.interest_tick_s",      "86400");
        Emulator.getConfig().register("rp.bank.interest_min_balance", "100");
        Emulator.getConfig().register("rp.bank.atm_room_ids",         "");

        Emulator.getConfig().register("rp.admin.min_rank",            "5");

        registerCommands();
        scheduleBankInterest();

        LOGGER.info("Pixeltower RP v{} — loaded (rp.* config keys registered, commands registered)", VERSION);
    }

    private void registerCommands() {
        CommandHandler.addCommand(new BalanceCommand());
        CommandHandler.addCommand(new GiveCommand());
        CommandHandler.addCommand(new OpenAccountCommand());
        CommandHandler.addCommand(new BankCommand());
        CommandHandler.addCommand(new DepositCommand());
        CommandHandler.addCommand(new WithdrawCommand());
        CommandHandler.addCommand(new TransferCommand());
        CommandHandler.addCommand(new AwardCommand());
    }

    private void scheduleBankInterest() {
        long tickS = Emulator.getConfig().getInt("rp.bank.interest_tick_s", 86400);
        // First run is tickS seconds from plugin-load so we don't double-pay on
        // rapid emulator restarts. Ops can trigger a manual tick via :rpreload
        // (future) or by flipping interest_tick_s briefly.
        Emulator.getThreading().getService().scheduleAtFixedRate(
                new BankInterestTask(), tickS, tickS, TimeUnit.SECONDS);
        LOGGER.info("BankInterestTask scheduled every {}s", tickS);
    }
}
