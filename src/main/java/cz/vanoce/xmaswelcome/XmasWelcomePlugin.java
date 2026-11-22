package cz.vanoce.xmaswelcome;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class XmasWelcomePlugin extends JavaPlugin implements CommandExecutor {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private NamespacedKey bookKey;
    private WelcomeListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bookKey = new NamespacedKey(this, "info_book");
        listener = new WelcomeListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        PluginCommand command = getCommand("xmaswelcome");
        if (command != null) {
            command.setExecutor(this);
        }

        // Po /reload nebo pozdním startu zpracujeme hráče, kteří už na serveru jsou
        for (Player player : getServer().getOnlinePlayers()) {
            listener.apply(player);
        }
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                listener.leave(player);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xmaswelcome.admin")) {
                sender.sendMessage(MM.deserialize("<red>Na to nemáš oprávnění."));
                return true;
            }
            reloadConfig();
            for (Player player : getServer().getOnlinePlayers()) {
                listener.apply(player);
            }
            sender.sendMessage(MM.deserialize("<green>Konfigurace načtena."));
        } else {
            sender.sendMessage(MM.deserialize("<yellow>Použití: /xmaswelcome reload"));
        }
        return true;
    }

    /** Je hráč ve vánočním světě? */
    public boolean inWorld(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(getConfig().getString("world", "Christmas"));
    }

    /** Vytvoří informační knížku podle configu. */
    public ItemStack createBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        List<String> pages = getConfig().getStringList("book.pages");
        item.editMeta(BookMeta.class, meta -> {
            meta.title(MM.deserialize(getConfig().getString("book.title", "Informace")));
            meta.author(MM.deserialize(getConfig().getString("book.author", "Server")));
            meta.displayName(MM.deserialize(getConfig().getString("book.name", "<gold>Informace o serveru"))
                    .decoration(TextDecoration.ITALIC, false));
            for (String page : pages) {
                meta.addPages(MM.deserialize(page));
            }
            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** Je tohle naše informační knížka? */
    public boolean isBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE);
    }
}
