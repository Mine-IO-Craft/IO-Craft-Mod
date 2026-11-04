package com.ble.mcbledevice.menu;

/**
 * 方块发送器与手持 ESP32-S3 编辑器共用的数据接口。
 * 两种 ScreenHandler 都暴露同一组 module / command / length / data 读写方法，
 * 使协议编辑页面可以完全复用。
 */
public interface SenderEditorData {

    byte getModule();

    byte getCommand();

    byte getLength();

    byte[] getData();

    void setModule(byte module);

    void setCommand(byte command);

    void setLength(byte length);

    void setData(byte[] data);
}