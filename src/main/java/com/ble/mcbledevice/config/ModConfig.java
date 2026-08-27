package com.ble.mcbledevice.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "mcbledevice")

//设置页面配置，同时也担任了各种bool状态的记录
public class ModConfig implements ConfigData {

    /*========================
     * 网络设置
     *========================*/

    public enum ConnectionMode {
        AUTO_DISCOVER,
        MANUAL
    }

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(
            option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public ConnectionMode connectionMode = ConnectionMode.MANUAL;

    @ConfigEntry.Gui.Tooltip
    public String deviceIp = "";

    @ConfigEntry.Gui.Tooltip
    public int devicePort = 12345;

    @ConfigEntry.Gui.Tooltip
    public boolean requestConnect = false;


    /*========================
     * 串口设置
     *========================*/

    @ConfigEntry.Gui.Tooltip
    public String serialPort = "COM3";

    @ConfigEntry.Gui.Tooltip
    public int baudRate = 9600;


    /*========================
     * 数据发送
     *========================*/

    @ConfigEntry.BoundedDiscrete(min = 500, max = 5000)
    public int sendIntervalMs = 1000;

    @ConfigEntry.Gui.CollapsibleObject
    public SendConfig send = new SendConfig();


    /*========================
     * 数据发送项
     *========================*/

    public static class SendConfig {

        @ConfigEntry.Gui.Tooltip
        public boolean health = false;

        @ConfigEntry.Gui.Tooltip
        public boolean food = false;

        @ConfigEntry.Gui.Tooltip
        public boolean saturation = false;

        @ConfigEntry.Gui.Tooltip
        public boolean position = false;

        @ConfigEntry.Gui.Tooltip
        public boolean level = false;

        @ConfigEntry.Gui.Tooltip
        public boolean difficulty = false;

        @ConfigEntry.Gui.Tooltip
        public boolean time = false;

        @ConfigEntry.Gui.Tooltip
        public boolean speed = false;
    }
}