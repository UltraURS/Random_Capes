package com.example.randomcapes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole config surface, stored as JSON.
 *
 * <p>两个加载器共用这一个配置：<em>游戏目录</em>/config/randomcapes.json。
 * 直接用 {@link Minecraft#gameDirectory} 而不走各家的 API（Fabric 的
 * {@code FabricLoader.getConfigDir()}、NeoForge 的 {@code FMLPaths.CONFIGDIR}），
 * 是为了让这一层保持中立 —— Fabric 与 NeoForge 的概念存储位置本来就是同一个目录。
 */
public final class RandomCapesConfig {
	/** 没单独设过权重的披风按这个算。 */
	public static final double DEFAULT_CAPE_WEIGHT = 1.0;

	/** 披风的随机方式。 */
	public enum ShuffleMode {
		/** 真随机：每次都从候选里瞎挑一个，挑过的下次还可能挑到。 */
		TRUE_RANDOM,
		/** 伪随机：一轮之内不重复，全轮完才开新一轮。 */
		PSEUDO_RANDOM
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(RandomCapes.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final String FILE_NAME = RandomCapes.MOD_ID + ".json";

	/** 前身模组的配置文件名，只在本配置文件还不存在时读一次。 */
	private static final String LEGACY_MOD_FILE = "urssauxiliarymod.json";

	/** 老配置里那个「自定义随机」模式的值，现在拆成了「随机模式 + 抽选跟随权重」。 */
	private static final String LEGACY_WEIGHTED_MODE = "WEIGHTED_RANDOM";

	private static RandomCapesConfig instance;

	/** 要不要在每次登录时随机切披风。默认开启。 */
	public boolean enabled = true;

	/** 披风的随机方式。 */
	public ShuffleMode shuffleMode = ShuffleMode.PSEUDO_RANDOM;

	/** 抽选时要不要按权重来（权重表见 {@link #capeWeights}）。 */
	public boolean weightedEnabled = false;

	/** 伪随机用：本轮已经抽到过的披风 id，抽满一轮就清空重新开始。 */
	public List<String> drawnCapes = new ArrayList<>();

	/** 权重表：披风 id → 权重（0~1）。没记录过的按 {@link #DEFAULT_CAPE_WEIGHT} 算。 */
	public Map<String, Double> capeWeights = new LinkedHashMap<>();

	public static RandomCapesConfig get() {
		if (instance == null) {
			instance = read();
		}

		return instance;
	}

	/** 把权重拉回 [0, 1]。 */
	public static double clampWeight(double weight) {
		return Math.max(0.0, Math.min(weight, 1.0));
	}

	/** 取某个披风的权重，没设过就是默认值。 */
	public double weightOf(String capeId) {
		Double weight = this.capeWeights.get(capeId);

		return weight == null ? DEFAULT_CAPE_WEIGHT : clampWeight(weight);
	}

	/** 配置文件的位置。游戏还没起来（理论上不该发生）时退到相对目录，不至于抛异常。 */
	private static Path configPath() {
		Minecraft client = Minecraft.getInstance();

		if (client == null) {
			return Path.of("config", FILE_NAME);
		}

		return client.gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
	}

	private static RandomCapesConfig read() {
		Path path = configPath();

		if (!Files.exists(path)) {
			// 第一次启用本模组：前身模组里那些披风设置（尤其是权重的辛苦）尽量接过来。
			return importLegacyOrNew(path);
		}

		return loadFrom(path);
	}

	/** 读一份配置；读不到就给默认值。 */
	private static RandomCapesConfig loadFrom(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			// 先按 JSON 读一遍再转对象：旧配置里可能还写着已经删掉的模式值，
			// 直接 fromJson 会卡在 Enum.valueOf 上抛异常，整份配置就丢了。
			JsonObject raw = JsonParser.parseReader(reader).getAsJsonObject();
			migrateLegacyMode(raw);

			RandomCapesConfig loaded = GSON.fromJson(raw, RandomCapesConfig.class);

			if (loaded == null) {
				return new RandomCapesConfig();
			}

			// 配置文件是纯文本，玩家的手改内容不一定守规矩。
			if (loaded.shuffleMode == null) {
				loaded.shuffleMode = ShuffleMode.PSEUDO_RANDOM;
			}
			if (loaded.drawnCapes == null) {
				loaded.drawnCapes = new ArrayList<>();
			}
			if (loaded.capeWeights == null) {
				loaded.capeWeights = new LinkedHashMap<>();
			}

			return loaded;
		} catch (Exception e) {
			LOGGER.warn("Could not read {}, using defaults: {}", path, e.toString());

			return new RandomCapesConfig();
		}
	}

	/**
	 * 本配置文件还不存在时的入口：先看一眼前身模组的配置在不在，在就把披风相关的
	 * 四项搬过来；否则老实给默认值。
	 */
	private static RandomCapesConfig importLegacyOrNew(Path path) {
		Path legacy = path.resolveSibling(LEGACY_MOD_FILE);

		if (!Files.exists(legacy)) {
			return new RandomCapesConfig();
		}

		RandomCapesConfig imported = loadFrom(legacy);

		LOGGER.info("Imported the cape settings from {} into {}.", LEGACY_MOD_FILE, FILE_NAME);

		// 立刻落盘，好让接下来一律走自己的配置文件。
		saveTo(imported, path);

		return imported;
	}

	/** 把老配置里的「自定义随机」换算成新写法：真随机 + 打开权重。 */
	private static void migrateLegacyMode(JsonObject raw) {
		JsonElement mode = raw.get("shuffleMode");

		if (mode == null || mode.isJsonNull() || !LEGACY_WEIGHTED_MODE.equals(mode.getAsString())) {
			return;
		}

		raw.addProperty("shuffleMode", ShuffleMode.TRUE_RANDOM.name());
		raw.addProperty("weightedEnabled", true);

		LOGGER.info("Migrated legacy '{}' mode to TRUE_RANDOM + weightedEnabled.", LEGACY_WEIGHTED_MODE);
	}

	public void save() {
		saveTo(this, configPath());
	}

	private static void saveTo(RandomCapesConfig config, Path path) {
		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (Exception e) {
			LOGGER.warn("Could not write {}: {}", path, e.toString());
		}
	}
}
