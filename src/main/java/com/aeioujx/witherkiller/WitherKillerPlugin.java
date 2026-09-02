package com.aeioujx.witherkiller;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import com.aeioujx.witherkiller.command.WitherKillerCommand;
import com.aeioujx.witherkiller.module.WitherKillerModule;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Automatically summons and kills withers with ZenithProxy",
    url = "https://github.com/AeiouJx/ZenithProxyWitherKiller",
    authors = {"AeiouJx"},
    mcVersions = {BuildConstants.MC_VERSION}
)
public class WitherKillerPlugin implements ZenithProxyPlugin {
    public static WitherKillerConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("WitherKiller plugin loading...");
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, WitherKillerConfig.class);
        pluginAPI.registerModule(new WitherKillerModule());
        pluginAPI.registerCommand(new WitherKillerCommand());
        LOG.info("WitherKiller plugin loaded!");
    }
}
