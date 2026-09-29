package com.example.randomcape;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks one of the capes the account already owns and makes it the active one,
 * so the switch is visible to everyone rather than only to clients running this mod.
 *
 * <p>只有客户端就是用得上它的地方，每次启动跑一次；结果写日志即可 —— 换披风要重启
 * 客户端才看得到，所以不需要给玩家做即时提示。
 *
 * <p>怎么挑由配置里的「随机模式」决定，见 {@link RandomCapeConfig.ShuffleMode}。
 * 这一层不认识 Fabric 也不认识 NeoForge，触发时机由各自的入口类决定。
 */
public final class CapeShuffler {
	/** 一个披风需要知道的两件事。 */
	public record Cape(String id, String alias) {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(RandomCape.MOD_ID);

	private static final URI PROFILE = URI.create("https://api.minecraftservices.com/minecraft/profile");
	private static final URI ACTIVE_CAPE = URI.create("https://api.minecraftservices.com/minecraft/profile/capes/active");
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	/** 最近一次成功读到的披风列表，给权重界面用。离线账号或还没读完时是空的。 */
	private static volatile List<Cape> knownCapes = List.of();

	private CapeShuffler() {
	}

	/** 已知的披风。界面线程读、网络线程写，所以是 volatile。 */
	public static List<Cape> knownCapes() {
		return knownCapes;
	}

	/**
	 * Runs one shuffle attempt on a worker thread - network I/O must never touch
	 * the client thread.
	 */
	public static void shuffleAsync(User user) {
		String token = user.getAccessToken();

		if (token == null || token.isBlank()) {
			LOGGER.info("No session token, skipping cape shuffle.");
			return;
		}

		Thread worker = new Thread(() -> {
			try {
				shuffle(token);
			} catch (Exception e) {
				LOGGER.warn("Cape shuffle failed: {}", e.toString());
			}
		}, RandomCape.MOD_ID + "-shuffle");
		worker.setDaemon(true);
		worker.start();
	}

	private static void shuffle(String token) throws Exception {
		HttpResponse<String> profileResponse = HTTP.send(
				authorized(PROFILE, token).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		// An account without a valid premium session cannot read its own profile.
		if (profileResponse.statusCode() == 401 || profileResponse.statusCode() == 403) {
			LOGGER.info("Not a genuine session, skipping cape shuffle.");
			return;
		}
		if (profileResponse.statusCode() != 200) {
			LOGGER.warn("Profile request returned HTTP {}, skipping cape shuffle.", profileResponse.statusCode());
			return;
		}

		JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
		JsonArray capes = profile.getAsJsonArray("capes");

		if (capes == null || capes.isEmpty()) {
			LOGGER.info("This account owns no capes, nothing to shuffle.");
			return;
		}

		List<Cape> all = new ArrayList<>();
		String activeId = null;

		for (JsonElement element : capes) {
			JsonObject cape = element.getAsJsonObject();
			String id = stringOr(cape, "id", null);

			if (id == null) {
				continue;
			}

			if ("ACTIVE".equals(stringOr(cape, "state", ""))) {
				activeId = id;
			}

			all.add(new Cape(id, stringOr(cape, "alias", id)));
		}

		knownCapes = List.copyOf(all);

		// 列表已经缓存好了（权重界面马上要用）。要不要真换披风是另一回事 ——
		// 以前这里先看 enabled 再决定拉不拉，结果把「随机披风」关掉之后权重界面
		// 永远是空的。
		RandomCapeConfig config = RandomCapeConfig.get();

		if (!config.enabled) {
			LOGGER.info("Cached {} owned cape(s), but random cape is switched off - keeping the current one.",
					all.size());
			return;
		}

		// 当前已经激活的那个换过去等于没换，所以不放进候选。
		String currentActive = activeId;
		List<Cape> candidates = all.stream()
				.filter(cape -> !cape.id().equals(currentActive))
				.toList();

		if (candidates.isEmpty()) {
			LOGGER.info("This account owns a single cape, nothing to shuffle.");
			return;
		}

		Cape picked = pick(candidates, config);

		// 伪随机的抽选记录得留下来，下次进游戏才接着轮到剩下的
		config.save();

		HttpResponse<String> setResponse = HTTP.send(
				authorized(ACTIVE_CAPE, token)
						.header("Content-Type", "application/json")
						.PUT(HttpRequest.BodyPublishers.ofString("{\"capeId\":\"" + picked.id() + "\"}"))
						.build(),
				HttpResponse.BodyHandlers.ofString());

		if (setResponse.statusCode() == 200) {
			LOGGER.info("Cape shuffled to '{}' [{} mode] (previous: {})",
					picked.alias(), config.shuffleMode, currentActive);
			return;
		}

		LOGGER.warn("Could not set cape, HTTP {}: {}", setResponse.statusCode(), setResponse.body());
	}

	/**
	 * 挑一个披风。三步：开权重时先剔掉权重 0 的 → 按「随机模式」定出本轮范围
	 * → 按「抽选跟随权重」决定均匀挑还是按权重挑。
	 *
	 * <p>伪随机那一步会更新 {@link RandomCapeConfig#drawnCapes}，
	 * 所以调用方要负责 {@link RandomCapeConfig#save()}。
	 */
	private static Cape pick(List<Cape> candidates, RandomCapeConfig config) {
		boolean pseudo = config.shuffleMode == RandomCapeConfig.ShuffleMode.PSEUDO_RANDOM;
		List<Cape> pool = candidates;

		// 开了权重就先剔掉权重 0 的披风。它们既不该被抽到，更不该占伪随机的
		// 一轮名额 —— 否则「本轮剩下的全是 0 权重」时总权重会是 0、退化成均匀
		// 随机，反而抽到玩家明确不想要的披风。
		if (config.weightedEnabled) {
			pool = candidates.stream()
					.filter(cape -> config.weightOf(cape.id()) > 0.0)
					.toList();

			if (pool.isEmpty()) {
				// 全被设成 0 了。按权重一个都抽不出来，退回均匀 —— 总比默默不换强。
				LOGGER.info("Every cape weight is zero, falling back to a uniform pick.");
				pool = candidates;
			}
		}

		List<Cape> round = pseudo ? remainingInRound(pool, config) : pool;
		Cape picked = config.weightedEnabled ? pickWeighted(round, config) : pickRandomly(round);

		if (pseudo) {
			config.drawnCapes.add(picked.id());

			LOGGER.info("Pseudo-random round: {} of {} capes drawn so far.",
					config.drawnCapes.size(), pool.size());
		}

		return picked;
	}

	private static Cape pickRandomly(List<Cape> pool) {
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	/**
	 * 本轮还没抽过的那些。都抽过一轮了就清空记录、重开一轮。
	 *
	 * <p>比较时是拿候选列表去对记录，而不是直接信记录 —— 记录里可能残留账号已经没有的
	 * 披风 id（比如官方下架了某个披风），拿它直接比会把“本轮”永久卡住。
	 */
	private static List<Cape> remainingInRound(List<Cape> candidates, RandomCapeConfig config) {
		List<Cape> remaining = candidates.stream()
				.filter(cape -> !config.drawnCapes.contains(cape.id()))
				.toList();

		if (remaining.isEmpty()) {
			LOGGER.info("Every cape has been drawn this round, starting a new round.");
			config.drawnCapes.clear();

			return candidates;
		}

		return remaining;
	}

	/**
	 * 按权重抽。调用方已经把 0 权重的剔掉了，所以正常进来时 total 必大于 0；
	 * 下面那条兜底防的是「所有权重都是 0」那条回退路径。
	 */
	private static Cape pickWeighted(List<Cape> pool, RandomCapeConfig config) {
		double total = 0.0;

		for (Cape cape : pool) {
			total += config.weightOf(cape.id());
		}

		if (total <= 0.0) {
			LOGGER.info("Every cape weight is zero, falling back to a uniform pick.");
			return pickRandomly(pool);
		}

		double roll = ThreadLocalRandom.current().nextDouble() * total;

		for (Cape cape : pool) {
			roll -= config.weightOf(cape.id());

			if (roll <= 0.0) {
				return cape;
			}
		}

		// 浮点误差兜底，正常走不到这儿。
		return pool.get(pool.size() - 1);
	}

	private static HttpRequest.Builder authorized(URI uri, String token) {
		return HttpRequest.newBuilder(uri)
				.header("Authorization", "Bearer " + token)
				.timeout(TIMEOUT);
	}

	private static String stringOr(JsonObject object, String member, String fallback) {
		return object.has(member) && !object.get(member).isJsonNull() ? object.get(member).getAsString() : fallback;
	}
}
