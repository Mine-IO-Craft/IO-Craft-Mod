package com.ble.mcbledevice.client.screen.protocol;

import java.util.List;

/**
 * 协议定义表（数据驱动）。
 *
 * 只描述 UI 需要知道的字段结构，不参与数据存储：
 * 发送器底层仍然只保存 module + cmd + len + data 的原始协议帧。
 */
public final class ProtocolRegistry {

    /** 一个数据字段：显示名 + 字节长度（2 字节字段按大端序编解码） */
    public record Field(String label, int bytes) {
    }

    /** 一条命令：cmd 编号 + 名称 + 数据字段列表 */
    public record Command(int id, String name, List<Field> fields) {
        public int dataLength() {
            int total = 0;
            for (Field field : fields) {
                total += field.bytes();
            }
            return total;
        }
    }

    /** 一个模块：module 编号 + 名称 + 已知命令列表 */
    public record Module(int id, String name, List<Command> commands) {
        public boolean hasCommands() {
            return !commands.isEmpty();
        }
    }

    private static final List<Module> MODULES = List.of(
            new Module(0x00, "系统信息", List.of()),
            new Module(0x01, "WS2812 灯带", List.of(
                    new Command(0x00, "灯珠数量设置", List.of(
                            new Field("灯珠数量", 1))),
                    new Command(0x01, "灯珠 RGB 设置", List.of(
                            new Field("灯珠编号", 1),
                            new Field("R", 1),
                            new Field("G", 1),
                            new Field("B", 1)))
            )),
            new Module(0x02, "Minecraft 数据", List.of()),
            new Module(0x03, "传感器", List.of()),
            new Module(0x04, "电机", List.of()),
            new Module(0x05, "显示屏", List.of()),
            new Module(0x06, "音频", List.of()),
            new Module(0x10, "GPIO", List.of(
                    // 输出设置：MC_Task_GPIO_Output_Set(gpio_num, output_level, open_drain, pull_up)
                    new Command(0x00, "GPIO 输出设置", List.of(
                            new Field("GPIO 编号", 1),
                            new Field("输出电平(0/1)", 1),
                            new Field("开漏模式(0/1)", 1),
                            new Field("上拉(0/1)", 1))),
                    // PWM：MC_Task_PWM_Set(gpio, timer, channel, freq, duty)，Freq/Duty 为大端 2 字节
                    new Command(0x02, "PWM 控制", List.of(
                            new Field("GPIO", 1),
                            new Field("TIM", 1),
                            new Field("CH", 1),
                            new Field("Freq(Hz)", 2),
                            new Field("Duty", 2)))
            )),
            // 预留：输入设置暂未提供协议，页面中显示为空位
            new Module(0xFF, "MAX", List.of())
    );

    private ProtocolRegistry() {
    }

    public static List<Module> modules() {
        return MODULES;
    }

    public static Module findModule(int id) {
        for (Module module : MODULES) {
            if (module.id() == id) {
                return module;
            }
        }
        return null;
    }

    public static Command findCommand(int moduleId, int cmdId) {
        Module module = findModule(moduleId);
        if (module == null) {
            return null;
        }
        for (Command command : module.commands()) {
            if (command.id() == cmdId) {
                return command;
            }
        }
        return null;
    }
}