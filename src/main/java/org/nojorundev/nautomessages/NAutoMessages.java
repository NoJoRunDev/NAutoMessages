package org.nojorundev.nautomessages;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatColor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NAutoMessages extends JavaPlugin {

    private FileConfiguration config;
    private int messageInterval;
    private boolean randomMessage;
    private Sound sound;
    private Map<String, List<String>> messagesMap;
    private String reloadMessage;
    private String permissionError;
    private String helpMessage;
    private BukkitRunnable messageTask; // Для управления задачей
    private int currentMessageIndex = 0; // Для циклического перебора сообщений

    @Override
    public void onEnable() {
        // Создание конфига, если его нет
        saveDefaultConfig();
        config = getConfig();

        // Загрузка настроек из конфига
        loadConfig();

        // Запуск задачи для отправки сообщений
        startMessageTask();

        // Регистрация команды
        Objects.requireNonNull(this.getCommand("nautomessages")).setExecutor(new ReloadCommand());
    }

    private void loadConfig() {
        messageInterval = config.getInt("interval", 60); // Время в секундах
        randomMessage = config.getBoolean("random", false); // Рандомное сообщение или нет
        String soundName = config.getString("sound", "BLOCK_NOTE_BLOCK_PLING"); // Звук
        sound = Sound.valueOf(soundName);

        // Загрузка сообщений
        messagesMap = new HashMap<>();
        ConfigurationSection messagesSection = config.getConfigurationSection("Messages");
        if (messagesSection != null) {
            for (String messageId : messagesSection.getKeys(false)) {
                List<String> messages = messagesSection.getStringList(messageId);
                messagesMap.put(messageId, messages);
            }
        }

        // Сбрасываем индекс при загрузке конфига
        currentMessageIndex = 0;

        // Загрузка кастомных сообщений
        reloadMessage = translateColors(config.getString("reload-message", "&aКонфигурация плагина успешно перезагружена!"));
        permissionError = translateColors(config.getString("permission-error", "&cУ вас нет прав на выполнение этой команды."));
        helpMessage = translateColors(config.getString("help", "&eИспользуйте: /nautomessages reload"));
    }

    private void startMessageTask() {
        // Останавливаем текущую задачу, если она существует
        if (messageTask != null) {
            messageTask.cancel();
        }

        // Создаем новую задачу
        messageTask = new BukkitRunnable() {
            @Override
            public void run() {
                String selectedMessageId = getRandomMessageId();
                List<String> messages = messagesMap.get(selectedMessageId);

                if (messages != null) {
                    for (String message : messages) {
                        // Отправляем сообщение с цветами через §
                        String formattedMessage = translateColors(message);
                        Bukkit.getOnlinePlayers().forEach(player -> {
                            player.sendMessage(formattedMessage);
                            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                        });
                    }
                }
            }
        };

        // Запускаем задачу с новым интервалом
        messageTask.runTaskTimer(this, 0, messageInterval * 20L); // Переводим секунды в тики
    }

    private String getRandomMessageId() {
        if (randomMessage) {
            List<String> messageIds = new ArrayList<>(messagesMap.keySet());
            return messageIds.get(new Random().nextInt(messageIds.size()));
        } else {
            // Циклически перебираем сообщения
            List<String> messageIds = new ArrayList<>(messagesMap.keySet());
            String messageId = messageIds.get(currentMessageIndex);

            // Увеличиваем индекс для следующего сообщения
            currentMessageIndex = (currentMessageIndex + 1) % messageIds.size();

            return messageId;
        }
    }

    @Override
    public void onDisable() {
        // Очистка ресурсов, если необходимо
    }

    // Класс для обработки команды /nautomessages reload
    private class ReloadCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("nautomessages.reload")) {
                    reloadConfig(); // Перезагружаем конфиг
                    config = getConfig(); // Обновляем ссылку на конфиг
                    loadConfig(); // Перезагружаем настройки

                    // Перезапускаем задачу с новыми настройками
                    startMessageTask();

                    // Отправляем сообщение с цветами через §
                    sender.sendMessage(reloadMessage);
                    return true; // Команда обработана успешно
                } else {
                    // Отправляем сообщение с цветами через §
                    sender.sendMessage(permissionError);
                    return true; // Команда обработана, но у игрока нет прав
                }
            }
            // Отправляем сообщение с цветами через §
            sender.sendMessage(helpMessage);
            return true; // Команда обработана, но аргументы неверны
        }
    }

    // Метод для замены & на § и поддержки HEX цветов
    private String translateColors(String message) {
        if (message == null) {
            return "";
        }

        // Сначала заменяем HEX цвета
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);

        // Затем заменяем & на § для стандартных цветов
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
