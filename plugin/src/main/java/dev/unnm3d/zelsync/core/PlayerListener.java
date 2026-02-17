package dev.unnm3d.zelsync.core;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.configs.Messages;
import dev.unnm3d.zelsync.configs.Settings;
import dev.unnm3d.zeltrade.api.enums.KnownRestriction;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;

public record PlayerListener(ZelSync plugin) implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!Settings.instance().rightClickToOpen) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player targetPlayer)) return;
        plugin.getTradeManager().openOrInviteTrade(event.getPlayer(), targetPlayer.getUniqueId(), targetPlayer.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getIgnoreManager().loadIgnoredPlayers(event.getPlayer().getName());

        //Update nameuuids
        plugin.getDataCache().updateCachePlayerList(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        plugin.getDataStorage().updateStoragePlayerList(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        plugin.getPlayerListManager().setPlayerNameUUID(event.getPlayer().getName(), event.getPlayer().getUniqueId());

        for (NewTrade playerActiveTrade : plugin.getTradeManager().queryPlayerTrades(event.getPlayer().getUniqueId())
                .actives().toList()
        ) {
            event.getPlayer().sendRichMessage(Messages.instance().tradeRunning
              .replace("%player%", playerActiveTrade
                .getTradeSide(playerActiveTrade.getActor(event.getPlayer()).opposite()).getTraderName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().distanceSquared(event.getTo()) < 0.1) return;
        plugin.getRestrictionService().triggerRestriction(event.getPlayer(), KnownRestriction.MOVED.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        plugin.getRestrictionService().triggerRestriction(p, KnownRestriction.MOUNT.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        plugin.getRestrictionService().triggerRestriction(p, KnownRestriction.MOUNT.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) return;
        plugin.getRestrictionService().triggerRestriction((Player) event.getEntity(), KnownRestriction.DAMAGED.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCombat(EntityDamageByEntityEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) return;
        if (event.getDamager().getType() != EntityType.PLAYER) return;
        plugin.getRestrictionService().triggerRestriction((Player) event.getDamager(), KnownRestriction.COMBAT.toString());
    }
}
