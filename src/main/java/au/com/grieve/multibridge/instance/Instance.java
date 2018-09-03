package au.com.grieve.multibridge.instance;

import au.com.grieve.multibridge.api.event.BuildEvent;
import au.com.grieve.multibridge.api.event.ReadyEvent;
import au.com.grieve.multibridge.util.SimpleTemplate;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Definition of an Instance
 */
public class Instance implements Listener {
    // Constants
    public enum StartMode {
        MANUAL, SERVER_START, SERVER_JOIN, INSTANCE_JOIN
    }

    public enum StopMode {
        MANUAL, SERVER_EMPTY, INSTANCE_EMPTY
    }

    public enum State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED,
        BUSY,
    }

    // Variables
    private InstanceManager manager;
    private Configuration instanceConfig;
    private Configuration templateConfig;
    private Path instanceFolder;
    private String name;
    private Integer port;
    private boolean bungeeRegistered = false;
    private State state = State.STOPPED;
    private boolean auto = false;

    // Tags
    private Map<String,String> tags;

    // Async IO
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Instance(InstanceManager manager, Path instanceFolder) throws InstantiationException {
        this.manager = manager;
        this.instanceFolder = instanceFolder;
        this.name = instanceFolder.getFileName().toString();

        // Make sure Folder Exists
        if (!Files.exists(instanceFolder)) {
            throw new InstantiationException("Instance folder does not exist");
        }

        // Register ourselves as a Listener
        manager.getPlugin().getProxy().getPluginManager().registerListener(manager.getPlugin(), this);

        loadConfig();

        // Handle Auto
//        switch(getStartMode()) {
//            case SERVER_START:
//                manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), () -> {
//                    System.out.println("[" + name + "] " + "Auto-Starting: Server Start");
//                    try {
//                        start();
//                    } catch (IOException e) {
//                        System.err.println("[" + name + "] " + "Failed to Start: " + e.getMessage());
//                    }
//                }, getStartDelay(), TimeUnit.SECONDS);
//                break;
//            case INSTANCE_JOIN:
//            case SERVER_JOIN:
//                autoEnabled = true;
//                setState(State.AUTO);
//                break;
//            case MANUAL:
//                setState(State.STOPPED);
//                break;
//        }

//        // Update State
//        update();
//
//        // Trigger Build Event if in pending state
//        if (getState() == State.PENDING) {
//            build();
//        }
//
//        // Auto-start if needed
//        if (getStartMode() == StartMode.SERVER_START && getState() == State.STOPPED) {
//            manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), () -> {
//                System.out.println("[" + name + "] " + "Auto-Starting: Server Start");
//                try {
//                    start();
//                } catch (IOException e) {
//                    System.out.println("[" + name + "] " + "Failed to Start: " + e.getMessage());
//                }
//            }, getStartDelay(), TimeUnit.SECONDS);
//
//        }
    }

    /**
     * Cleanup Instance before destroying
     */
    public void cleanUp() {

        // Unregister ourself as a Listener
        manager.getPlugin().getProxy().getPluginManager().unregisterListener(this);
    }

//    /**
//     * Build instance
//     */
//    public void build() {
//        manager.getPlugin().getProxy().getPluginManager().callEvent(new BuildEvent(this));
//    }

    private void loadConfig() {
        Path instanceConfigPath = instanceFolder.resolve("instance.yml");
        Path templateConfigPath = instanceFolder.resolve("template.yml");

        try {
            templateConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(templateConfigPath.toFile());
        } catch (IOException e) {
            templateConfig = new Configuration();
        }

        try {
            instanceConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(instanceConfigPath.toFile());
        } catch (IOException e) {
            instanceConfig = new Configuration();
        }
    }

//    /**
//     * Update our State
//     */
//    public void update() {
//        boolean ready = manager.getPlugin().getProxy().getPluginManager().callEvent(new ReadyEvent(this)).getReady();
//        boolean running = process != null;
//        StartMode startMode = getStartMode();
//
//        if (running) {
//            state = State.STARTED;
//        } else {
//            // Not running
//
//            if (ready) {
//                if (startMode == StartMode.INSTANCE_JOIN) {
//                    if (!hasRequiredTags()) {
//                        state = State.ERROR;
//                    } else {
//                        if (!bungeeRegistered) {
//                            System.out.println("[" + name + "]: Registering with BungeeCord");
//                            registerBungee();
//                        }
//                        state = State.WAITING;
//                    }
//                } else {
//                    if (bungeeRegistered) {
//                        System.out.print("[" + name + "]: Unregistering with BungeeCord");
//                    }
//                    state = State.STOPPED;
//                }
//            } else {
//                if (bungeeRegistered) {
//                    System.out.print("[" + name + "]: Unregistering with BungeeCord");
//                }
//                state = State.PENDING;
//            }
//        }
//    }

    /**
     * Save instanceConfig
     */
    private void saveConfig() {
        Path instanceConfigPath = instanceFolder.resolve("instance.yml");
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(instanceConfig, instanceConfigPath.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Clear tags cache
        tags = null;
    }

    /**
     * Return Tags set on this Instance
     */
    public Map<String, String> getLocalTags() {
        tags = new HashMap<>();
        // Add Instance Settings
        if (instanceConfig.contains("tags")) {
            for (String k : instanceConfig.getSection("tags").getKeys()) {
                tags.put(k.toUpperCase(), instanceConfig.getSection("tags").getString(k));
            }
        }
        return tags;
    }

    /**
     * Get tag set on this instance
     */
    public String getLocalTag(String key) {
        return instanceConfig.getString("tags." + key.toUpperCase());
    }

    /**
     * Get effective tag on this instance
     */
    public Map<String, String> getTags() {
        return getTags(true);
    }

    /**
     * Get effective tags for this instance
     */
    public Map<String, String> getTags(boolean refresh) {
        if (tags == null || refresh) {
            tags = new HashMap<>();

            // Add Defaults from Template
            if (templateConfig.contains("tags.defaults")) {
                for (String k : templateConfig.getSection("tags.defaults").getKeys()) {
                    tags.put(k.toUpperCase(), templateConfig.getSection("tags.defaults").getString(k));
                }
            }

            // Add Globals. Overrides above
            for (Map.Entry<String, String> e : manager.getPlugin().getGlobalManager().getTags().entrySet()) {
                tags.put(e.getKey(), e.getValue());
            }

            // Add Instance Settings
            if (instanceConfig.contains("tags")) {
                for (String k : instanceConfig.getSection("tags").getKeys()) {
                    tags.put(k.toUpperCase(), instanceConfig.getSection("tags").getString(k));
                }
            }
        }

        // Refresh Builtins
        tags.put("MB_SERVER_IP", "127.0.0.1");
        tags.put("MB_SERVER_PORT", port == null?"unknown":port.toString());
        tags.put("MB_SERVER_NAME", name);

        return tags;
    }

    /**
     * Get Tags this instance requires to start
     */
    public List<String> getRequiredTags() {
        return templateConfig.getStringList("tags.required");
    }

    /**
     * Register with Bungeecord
     */
    private void registerBungee() {
        if (bungeeRegistered) {
            return;
        }

        port = manager.getPort();
        ServerInfo info = manager.getPlugin().getProxy().constructServerInfo(
                name,
                new InetSocketAddress("127.0.0.1", port),
                name,
                true);
        manager.getPlugin().getProxy().getServers().put(name, info);
        bungeeRegistered = true;
    }

    /**
     * Unregister with Bungeecord
     */
    void unregisterBungee() {
        if (!bungeeRegistered) {
            return;
        }

        manager.getPlugin().getProxy().getServers().remove(name);
        manager.releasePort(port);
        port = null;
        bungeeRegistered = false;
    }

    /**
     * Set Auto
     */
    public void setAuto(Boolean auto) {
        if (auto) {
            if (getState() == State.STOPPED) {
                registerBungee();
            }
            this.auto = true;
        } else {
            if (getState() == State.STOPPED) {
                unregisterBungee();
            }
            this.auto = false;
        }
    }

    public boolean getAuto() {
        return this.auto;
    }

    /**
     * Start Instance
     */
    public void start() throws IOException {
        // Make sure we can start
        switch(getState()) {
            case STARTING:
                throw new IOException("Already Starting");
            case STARTED:
                throw new IOException("Already Started");
            case STOPPING:
                throw new IOException("Busy Stopping");
            case BUSY:
                throw new IOException("Instance is Busy");
        }

        try {
            // Update State to STARTING
            setState(State.STARTING);

            // Register with Bungee
            registerBungee();

            // Build Template Files
            SimpleTemplate st = new SimpleTemplate(getTags());
            updateTemplates(st);

            // Execute
            System.out.println("[" + name + "] " + "Starting Instance by executing: " + st.replace(templateConfig.getString("start.execute")));
            ProcessBuilder builder = new ProcessBuilder(st.replace(templateConfig.getString("start.execute")).split(" "));
            builder.redirectErrorStream(true);
            builder.directory(instanceFolder.toFile());
            process = builder.start();

            OutputStream stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();

            reader = new BufferedReader(new InputStreamReader(stdout));
            writer = new BufferedWriter(new OutputStreamWriter(stdin));

            manager.getPlugin().getProxy().getScheduler().runAsync(manager.getPlugin(), () -> {
                try {
                    for (String line; ((line = reader.readLine()) != null); ) {
                        System.out.println("[" + name + "] " + line);
                    }
                } catch (IOException ignored) {
                } finally {
                    try {
                        reader.close();
                        reader = null;
                    } catch (IOException ignored) {
                    }
                }

                System.out.println("[" + name + "] " + "Instance Shut Down");

                process = null;
                reader = null;
                writer = null;

                setState(State.STOPPED);
                if (!getAuto()) {
                    unregisterBungee();
                }
            });

            // Wait for Server to respond to a ping
            System.out.println("[" + name + "] " + "Waiting for Instance to become available");

            manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), new Runnable() {
                @Override
                public void run() {
                    Runnable pingRunnable = this;
                    manager.getPlugin().getProxy().getServers().get(getName()).ping((serverPing, ex) -> {
                        if (getState() == State.STARTING) {
                            if (serverPing == null) {
                                // Failed. Schedule to try again if we are still starting
                                manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), pingRunnable, 2, TimeUnit.SECONDS);
                                return;
                            }

                            System.out.println("[" + name + "] " + "Instance has started");

                            // Instance has started
                            setState(State.STARTED);

                            // If we have startup commands lets schedule that now
                            if (templateConfig.getStringList("start.commands").size() > 0) {
                                System.out.println("[" + name + "] Waiting to send Start Commands");
                                manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), () -> {
                                    if (isRunning()) {
                                        for (String cmd : templateConfig.getStringList("start.commands")) {
                                            try {
                                                System.out.println("[" + name + "] Sending Command: " + cmd);
                                                writer.write(cmd + "\n");
                                                writer.flush();
                                            } catch (IOException e) {
                                                break;
                                            }
                                        }
                                    }
                                }, templateConfig.getInt("start.delay", 30), TimeUnit.SECONDS);
                            }
                        }
                    });
                }
            }, 2, TimeUnit.SECONDS);

        } catch (Throwable e) {
            // Clean up if an error occurs
            process = null;
            setState(State.STOPPED);
            throw e;
        }
    }

    /**
     * Stop Instance
     */
    public void stop() throws IOException {
        // Make sure we can stop
        switch(getState()) {
            case STOPPING:
                throw new IOException("Already Stopping");
            case STOPPED:
                throw new IOException("Already Stopped");
            case BUSY:
                throw new IOException("Instance is Busy");
        }

        try {

            // Update out State
            setState(State.STOPPING);

            // Send Stop Commands
            if (reader != null && writer != null) {
                if (templateConfig.contains("stop.commands")) {
                    for (String command : templateConfig.getStringList("stop.commands")) {
                        try {
                            System.out.println("[" + name + "] " + "Sending command: " + command);
                            writer.write(command + "\n");
                            writer.flush();
                        } catch (IOException e) {
                            break;
                        }
                    }
                }
            }

            System.out.println("[" + name + "] " + "Waiting for Instance to shutdown");
            int maxTime = templateConfig.getInt("stop.delay", 5);
            while (process != null) {
                try {
                    Thread.sleep(1000);
                    maxTime -= 1;
                    if (maxTime < 1) {
                        break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            // Terminate task if needed
            if (process != null) {
                System.err.print("[" + name + "] " + "Murdering Instance");
                process.destroy();
            } else {
                System.out.println("[" + name + "] " + "Instance Cleanly Shut Down");
            }

        } catch (Throwable e) {
            setState(State.STOPPED);
            throw e;
        }
    }


    /**
     * Update Template files with placeholder values
     */
    private void updateTemplates(SimpleTemplate st) {
        // Statics for first run
        if (!getTagBoolean("MB_FIRST_RUN")) {
            for(String fileName:  templateConfig.getStringList("templates.static")) {
                try {
                    st.replace(instanceFolder.resolve(fileName + ".template"), instanceFolder.resolve(fileName));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Update instanceConfig
            setTag("MB_FIRST_RUN", "true");
        }

        // Update Dynamics
        for(String fileName:  templateConfig.getStringList("templates.dynamic")) {
            try {
                st.replace(instanceFolder.resolve(fileName + ".template"), instanceFolder.resolve(fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    /**
     * Remove Instance
     */
    public Integer getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    /**
     * Get Tag
     */
    public String getTag(String key) {
        return getTag(key, null);
    }

    public String getTag(String key, String def) {
        return getTags().getOrDefault(key, def);
    }

    public int getTagInt(String key, int def) {
        try {
            return Integer.parseInt(getTag(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int getTagInt(String key) {
        return getTagInt(key, 0);
    }

    public boolean getTagBoolean(String key) {
        return getTagBoolean(key, false);
    }

    public boolean getTagBoolean(String key, boolean def) {
        return Boolean.parseBoolean(getTag(key, String.valueOf(def)));
    }

    public void setTag(String key, String value) {
        instanceConfig.set("tags." + key, value);
        saveConfig();
    }

    public void clearTag(String key) {
        instanceConfig.set("tags." + key, null);
        saveConfig();
    }

    /**
     * Return Startup Type
     */
    public StartMode getStartMode() {
        try {
            return StartMode.valueOf(instanceConfig.getString("auto.start.mode", "MANUAL"));
        } catch (IllegalArgumentException e) {
            return StartMode.MANUAL;
        }
    }

    public void setStartMode(StartMode mode, int delay) {
        setTag("MB_START_MODE", mode.toString());
        instanceConfig.setString
    }

    /**
     * Return Startup Delay
     */
    public int getStartDelay() {
        return getTagInt("MB_START_DELAY", 0);
    }

    public void setStartDelay(int delay) {
        setTag("MB_START_DELAY", String.valueOf(delay));
    }

    /**
     * Get Stop Type
     */
    public StopMode getStopMode() {
        return StopMode.valueOf(getTag("MB_STOP_MODE", "MANUAL").toUpperCase());
    }

    public void setStopMode(StopMode mode) {
        setTag("MB_STOP_MODE", mode.toString());
    }


    /**
     * Get Stop Delay
     */
    public int getStopDelay() {
        return getTagInt("MB_STOP_DELAY", 0);
    }

    public void setStopDelay(int delay) {
        setTag("MB_STOP_DELAY", String.valueOf(delay));
    }

    public boolean hasRequiredTags() {
        Map<String, String> tags = getTags();
        for(String requiredTag: getRequiredTags()) {
            if (!tags.containsKey(requiredTag)) {
                return false;
            }
        }
        return true;
    }

    public Path getInstanceFolder() {
        return instanceFolder;
    }

    /**
     * Get State
     */
    public State getState() {
       return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Configuration getInstanceConfig() {
        return instanceConfig;
    }

    public Configuration getTemplateConfig() {
        return templateConfig;
    }

    public boolean isRunning() {
        return process != null;
    }

    /**
     * Reload Config
     */
    public void reloadConfig() {
        loadConfig();
    }

    /**
     * Check if we need to start before a player logs into the server
     */
    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        if (getAuto() && getState() == State.STOPPED && getStartMode() == StartMode.SERVER_JOIN) {
            manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), () -> {
                System.out.println("[" + name + "] " + "Auto-Starting: Server Join");
                try {
                    start();
                } catch (IOException e) {
                    System.out.println("[" + name + "] " + "Failed to Start: " + e.getMessage());
                }
            }, getStartDelay(), TimeUnit.SECONDS);
        }
    }

    /**
     * Check if we need to start before a player connects to our instance
     */
    @EventHandler
    public void onServerConnectEvent(ServerConnectEvent event) {
        if (event.getTarget().getName().equalsIgnoreCase(name)) {
            if (getAuto() && getState() == State.STOPPED && getStartMode() == StartMode.INSTANCE_JOIN) {
                // Cancel the event and wait for it to really come up
                event.setCancelled(true);

                manager.getPlugin().getProxy().getScheduler().runAsync(manager.getPlugin(), () -> {
                    System.out.println("[" + name + "] " + "Auto-Starting: Instance Join");
                    try {
                        start();
                    } catch (IOException e) {
                        System.out.println("[" + name + "] " + "Failed to Start: " + e.getMessage());
                        return;
                    }

                    // Get current time
                    Date date = new Date();
                    long startTime = date.getTime();

                    // Wait for Server to be up
                    manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), new Runnable() {
                        @Override
                        public void run() {
                            switch(getState()) {
                                case STARTING:
                                    if (date.getTime() - startTime > (getStartDelay()*1000)) {
                                        System.err.println("[" + name + "] " + "Failed to connect to Instance: Timed out");
                                        break;
                                    }
                                    manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), this, 2, TimeUnit.SECONDS);
                                    break;
                                case STARTED:
                                    // Send player to Server
                                    event.getPlayer().connect(event.getTarget());
                                    break;
                            }
                        }
                    }, 2, TimeUnit.SECONDS);
                });
            }
        }
    }

    /**
     * Check if the server is empty to shut down
     */
    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent event) {
        if (manager.getPlugin().getProxy().getPlayers().size() < 2) {
            if (getState() == State.STARTED && getStopMode() == StopMode.SERVER_EMPTY) {
                manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), () -> {
                    if (manager.getPlugin().getProxy().getPlayers().size() < 1) {
                        System.out.println("[" + name + "] " + "Auto-Stopping: Server Empty");
                        try {
                            stop();
                        } catch (IOException e) {
                            System.err.println("[" + name + "] " + "Failed to stop:" + e.getMessage());
                        }
                    }
                }, getStopDelay(), TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Check if instance is empty to shut down
     */
    @EventHandler
    public void onServerDisconnectEvent(ServerDisconnectEvent event) {
        if (event.getTarget().getName().equals(name) && event.getTarget().getPlayers().size() < 2) {
            if (getState() == State.STARTED  && getStopMode() == StopMode.INSTANCE_EMPTY) {
                manager.getPlugin().getProxy().getScheduler().schedule(manager.getPlugin(), () -> {
                    if (event.getTarget().getPlayers().size() < 1) {
                        System.out.println("[" + name + "] " + "Auto-Stopping: Instance Empty");
                        try {
                            stop();
                        } catch (IOException e) {
                            System.err.println("[" + name + "] " + "Failed to stop:" + e.getMessage());
                        }
                    }
                }, getStopDelay(), TimeUnit.SECONDS);
            }
        }
    }

}