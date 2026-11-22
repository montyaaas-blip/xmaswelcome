package cz.vanoce.xmaswelcome;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WelcomeListener implements Listener {

    private final XmasWelcomePlugin plugin;
    /** Hráči, kterým jsme zapnuli povolení k letu kvůli double jumpu. */
    private final Set<UUID> jumpers = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public WelcomeListener(XmasWelcomePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Připojení, svět, respawn, gamemode
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            apply(player);
            welcome(player);
        }, 20L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leave(player);
        cooldowns.remove(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Knížka nemá po smrti spadnout na zem
        event.getDrops().removeIf(plugin::isBook);
    }

    // ------------------------------------------------------------------
    // Uvítání
    // ------------------------------------------------------------------

    private void welcome(Player player) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("welcome.enabled", true)) {
            return;
        }
        for (String line : config.getStringList("welcome.lines")) {
            player.sendMessage(XmasWelcomePlugin.MM.deserialize(line,
                    Placeholder.unparsed("player", player.getName())));
        }
    }

    // ------------------------------------------------------------------
    // Double jump
    // ------------------------------------------------------------------

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || !jumpers.contains(player.getUniqueId()) || !plugin.inWorld(player)) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return;
        }

        // Hráč nemá létat, jen se odrazit
        event.setCancelled(true);
        player.setFlying(false);

        FileConfiguration config = plugin.getConfig();
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        cooldowns.put(player.getUniqueId(), now + config.getLong("doublejump.cooldown-ms", 1000L));

        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() > 1.0E-6) {
            direction.normalize().multiply(config.getDouble("doublejump.forward", 1.0));
        } else {
            direction = new Vector(0, 0, 0);
        }
        direction.setY(config.getDouble("doublejump.up", 0.9));
        player.setVelocity(direction);

        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.7f, 1.4f);
        player.spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);

        String message = config.getString("doublejump.message", "");
        if (!message.isEmpty()) {
            player.sendMessage(XmasWelcomePlugin.MM.deserialize(message));
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getConfig().getBoolean("doublejump.no-fall-damage", true) && plugin.inWorld(player)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Ochrana knížky (nejde přesunout, zahodit ani přehodit do druhé ruky)
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        boolean touchesBook = plugin.isBook(event.getCurrentItem()) || plugin.isBook(event.getCursor());
        if (!touchesBook && event.getClick() == ClickType.NUMBER_KEY) {
            touchesBook = plugin.isBook(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (touchesBook) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.isBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.isBook(event.getMainHandItem()) || plugin.isBook(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Zapnutí / vypnutí funkcí podle světa
    // ------------------------------------------------------------------

    /** Podle světa hráči zapne (ve vánočním světě), nebo vypne (jinde) double jump a knížku. */
    public void apply(Player player) {
        if (plugin.inWorld(player)) {
            enter(player);
        } else {
            leave(player);
        }
    }

    private void enter(Player player) {
        GameMode mode = player.getGameMode();
        boolean survival = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
        if (survival && plugin.getConfig().getBoolean("doublejump.enabled", true)
                && player.hasPermission("xmaswelcome.doublejump")) {
            player.setAllowFlight(true);
            jumpers.add(player.getUniqueId());
        } else if (jumpers.remove(player.getUniqueId()) && survival) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        giveBook(player);
    }

    public void leave(Player player) {
        removeBook(player);
        if (jumpers.remove(player.getUniqueId())) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }

    private void giveBook(Player player) {
        removeBook(player);
        PlayerInventory inventory = player.getInventory();
        int slot = Math.max(0, Math.min(8, plugin.getConfig().getInt("book.slot", 4)));

        // Pokud je ve slotu jiný předmět, přesuneme ho jinam (případně upustíme)
        ItemStack current = inventory.getItem(slot);
        if (current != null && !current.getType().isAir()) {
            inventory.setItem(slot, null);
            for (ItemStack left : inventory.addItem(current).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        inventory.setItem(slot, plugin.createBook());
    }

    private void removeBook(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (plugin.isBook(inventory.getItem(i))) {
                inventory.setItem(i, null);
            }
        }
    }
}
