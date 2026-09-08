package com.ble.mcbledevice;

import com.ble.mcbledevice.blocks.ModBlockEntities;
import com.ble.mcbledevice.blocks.ModBlocks;
import com.ble.mcbledevice.blockentity.McSenderBlockEntity;
import com.ble.mcbledevice.config.ConfigManager;
import com.ble.mcbledevice.config.ModConfig;
import com.ble.mcbledevice.data.PlayerData;
import com.ble.mcbledevice.handler.BleHandler;
import com.ble.mcbledevice.handler.SerialHandler;
import com.ble.mcbledevice.item.ModItemGroups;
import com.ble.mcbledevice.item.ModItems;
import com.ble.mcbledevice.menu.ModScreenHandlers;
import com.ble.mcbledevice.network.McSenderUpdatePayload;
import com.ble.mcbledevice.tools.NetworkHandler;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 我的世界模组类
 * 在这里进行代码编辑，初始化的时候加载到游戏
 */
public class McBleDevice implements ModInitializer {

	private static long lastSendTime = 0;

	//private static final long SEND_INTERVAL_MS = 100; // 10次/秒
	//private static long lastSendTime = 0;

	public static final String MOD_ID = "mcbledevice";

	private static final Map<UUID, PlayerData> lastDataMap = new HashMap<>();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 1. 初始化配置 创建配置对象
		//从 json5/toml 读取配置
		//注册 AutoConfig
		ConfigManager.init();

		// 2. 启动串口连接（异步）
		SerialHandler.initialize();

		// 3. 注册物品 + 类别 + 方块
		ModItems.registerModItems();
		ModItemGroups.registerModItemGroups();
		ModBlocks.registerModBlocks();

		ModBlockEntities.register();
		ModScreenHandlers.register();
		// 注册Payload类型
		PayloadTypeRegistry.playC2S()
				.register(
						McSenderUpdatePayload.ID,
						McSenderUpdatePayload.CODEC
				);
		// 注册接收器
		ServerPlayNetworking.registerGlobalReceiver(
				McSenderUpdatePayload.ID,
				(payload, context) -> {

					McBleDevice.LOGGER.info(
							"Received sender payload pos={}",
							payload.pos()
					);

					context.server().execute(() -> {

						var world =
								context.player()
										.getWorld();
						var be =
								world.getBlockEntity(
										payload.pos()
								);
						McBleDevice.LOGGER.info(
								"BlockEntity={}",
								be
						);

						// 防止改包客户端隔空修改任意位置的发送器：距离超过 64 格直接忽略
						boolean inRange = context.player().getBlockPos()
								.getSquaredDistance(payload.pos()) <= 4096.0;
						if(inRange && be instanceof McSenderBlockEntity sender)
						{

							sender.module = payload.module();
							sender.command = payload.command();
							sender.length = payload.length();
							sender.data = payload.data();

							sender.markUpdated();
							McBleDevice.LOGGER.info(
									"MC Sender Saved: module={} command={} length={}",
									sender.module,
									sender.command,
									sender.length
							);
						}
					});

				}
		);
		// 初始化网络通信（不开启连接）
		NetworkHandler.initialize();
		// 初始化 BLE（不自动连接，需在配置页手动扫描/连接）
		BleHandler.initialize();
		// 连接测试
		//NetworkHandler.connect();

		// 4. 注册服务端 Tick 事件，采集并发送玩家数据
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = System.currentTimeMillis();
			ModConfig config = ConfigManager.getConfig();
			//>>>>>>>>>>>>>>用于UDP TCP连接->>>>>>>>>>>>>>

			if(config != null && config.requestConnect)
			{
				LOGGER.info("收到连接请求");
				NetworkHandler.connect();
				config.requestConnect = false;
				ConfigManager.save();
			}
			//^^^^^^^^^^^^^^^用于UDP TCP连接^^^^^^^^^^^^^^^^^
			//>>>>>>>>>>>>>>用于周期发送数据->>>>>>>>>>>>>>
			if(config == null)
			{
				return;
			}

			if (now - lastSendTime < config.sendIntervalMs)
			{
				return;
			}

			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				UUID uuid = player.getUuid();
				PlayerData current = new PlayerData(player);
				PlayerData previous = lastDataMap.get(uuid);

				if (previous == null || !current.equals(previous)) {
					List<byte[]> packets = current.buildAllPackets(config);
					for (byte[] packet : packets) {
						// 调试用，生产环境建议改为 debug
						LOGGER.debug("发送数据包: {}", Arrays.toString(packet));
						//通过串口发送
						SerialHandler.sendData(packet);
						//通过网络发送
						NetworkHandler.sendData(packet);
						//通过BLE发送
						BleHandler.sendData(packet);
					}
					lastDataMap.put(uuid, current);
				}
			}
			//^^^^^^^^^^^^^^^用于周期发送数据^^^^^^^^^^^^^^^^^
			lastSendTime = now; // 更新发送时间戳
		});


		// 玩家断开时清理其缓存数据，避免 lastDataMap 只增不减
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			lastDataMap.remove(handler.player.getUuid());
		});

		// 服务器停止时优雅释放串口 / TCP / BLE 资源
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SerialHandler.shutdown();
			NetworkHandler.shutdown();
			BleHandler.shutdown();
		});

		LOGGER.info("IO-Craft 模组加载完成！");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
