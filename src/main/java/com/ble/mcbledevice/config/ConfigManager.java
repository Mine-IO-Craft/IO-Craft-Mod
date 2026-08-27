package com.ble.mcbledevice.config;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

public class ConfigManager {

    private static ModConfig config;

    public static void init() {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);    //注册配置
        config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();   //拿到配置实例
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static void save() {
        AutoConfig.getConfigHolder(ModConfig.class).save();
    }

}