package org.nyxcode.wurm.discordrelay;

import com.wurmonline.server.*;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.kingdom.Kingdom;
import com.wurmonline.server.kingdom.Kingdoms;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.villages.PvPAlliance;
import com.wurmonline.server.villages.Village;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import mod.sin.lib.Prop;
import mod.sin.lib.Util;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Created by whisper2shade on 22.04.2017.
 */
public class DiscordRelay extends ListenerAdapter implements WurmServerMod, PreInitable, Configurable, ServerPollListener, ChannelMessageListener, PlayerMessageListener, Versioned {
    public static final Logger logger = Logger.getLogger(DiscordRelay.class.getName());
    public static final String version = "ty3.2";

    protected static JDA jda;
    protected static String botToken = "";
    protected static String serverName = "";
    //private String wurmBotName;
    protected static boolean useUnderscore = false;
    protected static boolean showConnectedPlayers = true;
    protected static int connectedPlayerUpdateInterval = 120;
    protected static boolean enableRumors = true;
    protected static String rumorChannel = "rumors";
    protected static boolean enableTrade = true;
    protected static boolean enableMGMT = true;
    protected static boolean enableCAHELP = true;
    protected static boolean countAltsAsPlayers = true;

    private static final HashMap<String, String> emojis = new HashMap<>();

    static {
        emojis.put("\uD83D\uDE1B", ":P");
        emojis.put("\uD83D\uDE03", ":)");
        emojis.put("\uD83D\uDE04", ":D");
        emojis.put("\uD83D\uDE26", ":(");
        emojis.put("\uD83D\uDE22", ":`(");
        emojis.put("\uD83D\uDE17", ":*");
        emojis.put("\uD83D\uDC94", "</3");
        emojis.put("\u2764", "<3");
        emojis.put("\uD83D\uDE01", ":]");
        emojis.put("\uD83D\uDE09", ";)");
        emojis.put("\uD83D\uDE20", ">:(");
        emojis.put("\uD83D\uDE21", ">:(");
        emojis.put("\uD83D\uDE2D", ";-;");
    }

    @Override
    public void configure(Properties properties) {
        Prop.properties = properties;

        botToken = properties.getProperty("botToken", botToken);
        if(botToken.equals("")){
            logger.warning("Discord bot token not entered for DiscordRelay. The bot will not function without this.");
        }
        serverName = properties.getProperty("discordServerName", serverName);
        if(serverName.equals("")){
            logger.warning("Server name not entered for DiscordRelay. The bot will not function without this.");
        }
        useUnderscore = Boolean.parseBoolean(properties.getProperty("useUnderscore", Boolean.toString(useUnderscore)));
        showConnectedPlayers = Boolean.parseBoolean(properties.getProperty("showConnectedPlayers", Boolean.toString(showConnectedPlayers)));
        connectedPlayerUpdateInterval = Integer.parseInt(properties.getProperty("connectedPlayerUpdateInterval", Integer.toString(connectedPlayerUpdateInterval)));
        pollPlayerInterval = TimeConstants.SECOND_MILLIS*connectedPlayerUpdateInterval;
        enableRumors = Boolean.parseBoolean(properties.getProperty("enableRumors", Boolean.toString(enableRumors)));
        rumorChannel = properties.getProperty("rumorChannel", rumorChannel);
        enableTrade = Boolean.parseBoolean(properties.getProperty("enableTrade", Boolean.toString(enableTrade)));
        enableMGMT = Prop.getBooleanProperty("enableMGMT", enableMGMT);
        enableCAHELP = Prop.getBooleanProperty("enableCAHELP", enableCAHELP);
        countAltsAsPlayers = Prop.getBooleanProperty("countAltsAsPlayers", countAltsAsPlayers);
    }

    @Override
    public void preInit() {
        ClassPool classPool = HookManager.getInstance().getClassPool();
        Class<DiscordRelay> thisClass = DiscordRelay.class;

        // jda = JDABuilder.createDefault(botToken).addEventListeners(this).build().awaitReady();
        jda = JDABuilder.create(botToken, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.CLIENT_STATUS, CacheFlag.STICKER, CacheFlag.ONLINE_STATUS)
                .addEventListeners(this)
                .build();

        // - Send rumour messages to discord - //
        try {
            if(enableRumors) {
                CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
                CtClass[] params1 = {
                        CtClass.intType,
                        CtClass.booleanType,
                        CtClass.floatType,
                        CtClass.floatType,
                        CtClass.floatType,
                        CtClass.intType,
                        classPool.get("java.lang.String"),
                        CtClass.byteType,
                        CtClass.byteType,
                        CtClass.byteType,
                        CtClass.booleanType,
                        CtClass.byteType,
                        CtClass.intType
                };
                String desc1 = Descriptor.ofMethod(ctCreature, params1);
                Util.setReason("Send rumour messages to Discord.");
                String replace = "$proceed($$);"
                                    + DiscordRelay.class.getName() + ".sendRumour(toReturn);";
                Util.instrumentDescribed(thisClass, ctCreature, "doNew", desc1, "broadCastSafe", replace);
            }
        } catch (NotFoundException e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public static void sendRumour(Creature creature){
        sendToDiscord(rumorChannel, "Rumours of " + creature.getName() + " are starting to spread.", true);
    }


    private static final DateFormat df = new SimpleDateFormat("HH:mm:ss");
    public static void sendToDiscord(String channel, String message, boolean includeMap){
        MessageCreateBuilder builder = new MessageCreateBuilder();
        message = "[" + df.format(new Date(System.currentTimeMillis())) + "] "+message; // Add timestamp
        if(includeMap) {
            message = message + " (" + Servers.localServer.mapname + ")";
        }

        builder.addContent(message);
        try {
            jda.getGuildsByName(serverName, true).get(0).getTextChannelsByName(channel, true).get(0).sendMessage(builder.build()).queue();
        }catch(Exception e){
            logger.log(Level.WARNING, "Discord Relay failure: #"+channel+" - "+message, e);
        }
    }

    @Override
    public MessagePolicy onKingdomMessage(Message message) {
        String window = message.getWindow();
        if(enableTrade && window.startsWith("Trade")){
            sendToDiscord("trade", message.getMessage(), false);
        }else if(window.startsWith("GL-")){
            byte kingdomId = message.getSender().getKingdomId();

            String kingdomName = discordifyName("GL-"+Kingdoms.getChatNameFor(kingdomId));
            sendToDiscord(kingdomName, message.getMessage(), false);
        }else{
            byte kingdomId = message.getSender().getKingdomId();
            String kingdomName = discordifyName(Kingdoms.getChatNameFor(kingdomId));
            sendToDiscord(kingdomName, message.getMessage(), false);
        }

        return MessagePolicy.PASS;
    }

    public void sendToHelpChat(final String channel, final String message){
        String window = "CA HELP";
        final Message mess = new Message(null, Message.CA, window, message);
        //mess.setSenderKingdom((byte) 4);
        byte kingdomId = 4;
        if (message.trim().length() > 1) {
            Players.getInstance().sendCaMessage(kingdomId, mess);
        }
    }

    public void sendToMGMTChat(final String channel, final String message){
        String window = "MGMT";
        final Message mess = new Message(null, Message.MGMT, window, message);
        mess.setSenderKingdom((byte) 4);
        if (message.trim().length() > 1) {
            Server.getInstance().addMessage(mess);
        }
    }

    public void sendToTradeChat(final String channel, final String message){
        String window = "Trade";
        final Message mess = new Message(null, Message.TRADE, window, message);
        mess.setSenderKingdom((byte) 4);
        if (message.trim().length() > 1) {
            Server.getInstance().addMessage(mess);
        }
    }

    public void sendToGlobalKingdomChat(final String channel, final String message) {
        Kingdom[] kingdoms = Kingdoms.getAllKingdoms();

        byte kingdomId = -1;
        boolean global = false;

        for (Kingdom kingdom : kingdoms) {
            if (discordifyName("GL-"+Kingdoms.getChatNameFor(kingdom.getId())).equals(channel.toLowerCase())) {
                kingdomId = kingdom.getId();
                global = true;
                break;
            }else if(discordifyName(Kingdoms.getChatNameFor(kingdom.getId())).equals(channel.toLowerCase())){
                kingdomId = kingdom.getId();
                global = false;
                break;
            }
        }
        if (kingdomId != -1) {

            String window = "";
            if(global){
                window = window + "GL-";
            }
            window = window + Kingdoms.getChatNameFor(kingdomId);

            final Message mess = new Message(null, Message.GLOBKINGDOM, window, message);
            mess.setSenderKingdom(kingdomId);
            if (message.trim().length() > 1) {
                Server.getInstance().addMessage(mess);
            }
        }
    }

    @Override
    public MessagePolicy onVillageMessage(Village village, Message message) {
        return MessagePolicy.PASS;
    }

    @Override
    public MessagePolicy onAllianceMessage(PvPAlliance alliance, Message message) {
        return MessagePolicy.PASS;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        super.onMessageReceived(event);
        
        if (event.isFromType(ChannelType.TEXT) && !event.getAuthor().isBot()) {
            String channelName = event.getChannel().getName();
            if(event.getMember() != null) {
                String authorName = event.getMember().getEffectiveName();

                String message = event.getMessage().getContentDisplay().trim();
                for (Map.Entry<String, String> p : emojis.entrySet()) {
                    message = message.replace(p.getKey(), p.getValue());
                }

                if (enableTrade && channelName.contains("trade")) {
                    sendToTradeChat(channelName, "<@" + authorName + "> " + message);
                } else if (enableCAHELP && channelName.contains(discordifyName("ca-help"))) {
                    sendToHelpChat(channelName, "<@" + authorName + "> " + message);
                } else if (enableMGMT && channelName.contains("mgmt")) {
                    sendToMGMTChat(channelName, "<@" + authorName + "> " + message);
                } else {
                    sendToGlobalKingdomChat(channelName, "<@" + authorName + "> " + message);
                }
            }
        }
    }

    private String discordifyName(String name) {
        name = name.toLowerCase();
        if (useUnderscore) {
            return name.replace(" ", "_");
        } else {
            return name.replace(" ", "");
        }
    }

    protected static String getPlayerPrefix(Communicator comm){
        if (comm.getPlayer() != null) {
            return "<" + comm.getPlayer().getName() + "> ";
        }
        logger.warning("Could not find player for a communicator.");
        return "<???>";
    }

    @Override
    public MessagePolicy onPlayerMessage(Communicator communicator, String message, String title){
        // Skip commands
        if (!message.startsWith("!") && !message.startsWith("/") && !message.startsWith("#")) {
            if (enableMGMT && title.equals("MGMT")) {
                sendToDiscord("mgmt", getPlayerPrefix(communicator) + message, false);
            } else if (enableCAHELP && title.equals("CA HELP")) {
                sendToDiscord(discordifyName("ca-help"), getPlayerPrefix(communicator) + message, false);
            }
        }
        return MessagePolicy.PASS;
    }

    @Override
    public boolean onPlayerMessage(Communicator communicator, String message) {
        return false;
    }

    protected static long lastPolledPlayers = 0;
    protected static long pollPlayerInterval = TimeConstants.SECOND_MILLIS*120;
    @Override
    public void onServerPoll() {
        if(showConnectedPlayers) {
            if(System.currentTimeMillis() > lastPolledPlayers + pollPlayerInterval) {
                if (Servers.localServer.LOGINSERVER) {
                    try {
                        int numPlayers;
                        if(countAltsAsPlayers){
                            numPlayers = Players.getInstance().getNumberOfPlayers();
                        }else{
                            Player[] players = Players.getInstance().getPlayers();

                            HashSet<Long> ids = new HashSet<>();
                            for(Player player : players){
                                ids.add(player.getSaveFile().getSteamId().getSteamID64());
                            }

                            numPlayers = ids.size();
                        }
                        jda.getPresence().setActivity(Activity.of(Activity.ActivityType.PLAYING,  numPlayers+" online!"));
                    }catch(Exception e){
                        logger.warning("Failed to update player count."+e);
                    }
                }
                lastPolledPlayers = System.currentTimeMillis();
            }
        }
    }

    @Override
    public String getVersion(){
        return version;
    }
}