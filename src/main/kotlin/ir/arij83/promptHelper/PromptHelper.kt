package ir.arij83.promptHelper

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class PromptHelper : JavaPlugin(), Listener, CommandExecutor {
    private lateinit var promptsConfig: FileConfiguration
    private lateinit var messagesConfig: FileConfiguration
    private lateinit var config: FileConfiguration
    private var triggerWord: String = "phelper"
    private val lastMessageTime = HashMap<UUID, Long>()

    override fun onEnable() {
        saveDefaultConfig()
        loadConfigs()
        server.pluginManager.registerEvents(this, this)
        getCommand("phelper")?.setExecutor(this)
        logger.info("Plugin Enabled successfully")
        logger.info("By AriJ83 - Ver: 1.0")
    }

    override fun onDisable() {
        logger.info("PromptHelper Plugin Disabled!")
    }

    private fun loadConfigs() {
        reloadConfig()
        config = getConfig()
        triggerWord = config.getString("trigger_word", "phelper")!!

        val promptsFile = File(dataFolder, "prompts.yml")
        if (!promptsFile.exists()) saveResource("prompts.yml", false)
        promptsConfig = YamlConfiguration.loadConfiguration(promptsFile)

        val messagesFile = File(dataFolder, "messages.yml")
        if (!messagesFile.exists()) saveResource("messages.yml", false)
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile)
    }


    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val message = event.message.lowercase()

        // Anti-Spam
        val cooldownTime = config.getLong("anti_spam.cooldown_seconds", 5) * 1000
        val lastTime = lastMessageTime[player.uniqueId] ?: 0
        if (System.currentTimeMillis() - lastTime < cooldownTime) {
            event.isCancelled = true
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>You are sending messages too quickly!</red>"))
            return
        }
        lastMessageTime[player.uniqueId] = System.currentTimeMillis()

        // Anti-Exploit
        val maxLength = config.getInt("anti_exploit.max_message_length", 256)
        if (message.length > maxLength) {
            event.isCancelled = true
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Your message is too long!</red>"))
            return
        }
        if (config.getBoolean("anti_exploit.block_special_chars", true) && message.contains(Regex("[^a-zA-Z0-9 _-]"))) {
            event.isCancelled = true
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Your message contains illegal characters!</red>"))
            return
        }

        if (message == triggerWord) {
            event.isCancelled = true
            Bukkit.getScheduler().runTask(this, Runnable { openPromptMenu(player) })
            return
        }


        if (promptsConfig.contains(message)) {
            event.isCancelled = true
            val response = promptsConfig.getString(message) ?: ""
            val formattedResponse = MiniMessage.miniMessage().deserialize(messagesConfig.getString("prefix", "") + response)
            player.sendMessage(formattedResponse)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val item = event.currentItem ?: return
        val promptKey = item.itemMeta?.displayName ?: return

        if (promptsConfig.contains(promptKey)) {
            event.isCancelled = true
            player.closeInventory()
            val response = promptsConfig.getString(promptKey) ?: ""
            val formattedResponse = MiniMessage.miniMessage().deserialize(messagesConfig.getString("prefix", "") + response)
            player.sendMessage(formattedResponse)
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("phelper", ignoreCase = true)) {
            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                if (sender.hasPermission("prompthelper.admin")) {
                    reloadConfig()
                    loadConfigs()
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(messagesConfig.getString("plugin_reloaded") ?: "<green>Plugin reloaded successfully!</green>"))
                } else {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(messagesConfig.getString("no_permission") ?: "<red>You do not have permission to do this!</red>"))
                }
                return true
            }
        }
        return false
    }

    private fun openPromptMenu(player: Player) {
        val inventory: Inventory = Bukkit.createInventory(null, 9, MiniMessage.miniMessage().deserialize(config.getString("menu_title", "<yellow>Prompt Menu</yellow>")!!))
        val defaultMaterial = Material.matchMaterial(config.getString("menu_item_material", "PAPER")!!.uppercase()) ?: Material.PAPER

        for (key in promptsConfig.getKeys(false)) {
            val item = ItemStack(defaultMaterial)
            val meta: ItemMeta = item.itemMeta!!
            meta.displayName(MiniMessage.miniMessage().deserialize(key))
            item.itemMeta = meta
            inventory.addItem(item)
        }

        player.openInventory(inventory)
    }
}
