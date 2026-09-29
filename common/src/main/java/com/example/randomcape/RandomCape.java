package com.example.randomcape;

import net.minecraft.client.User;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 两个加载器共用的入口点。
 *
 * <p>抽披风每次启动只应该跑一次。Fabric 那侧有「客户端启动完成」事件可以用，而
 * NeoForge 这版为了少依赖一个事件体系，挂的是 tick 事件（每秒二十次）—— 所以这里
 * 用 {@link AtomicBoolean} 统一挡一道，调用方不必自己管去重。
 */
public final class RandomCape {
	/** 模组 id，也是资源命名空间和配置文件名的前缀。 */
	public static final String MOD_ID = "randomcape";

	private static final AtomicBoolean SHUFFLED = new AtomicBoolean();

	private RandomCape() {
	}

	/** 客户端可用之后由各加载器调用；重复调用只有第一次会真正生效。 */
	public static void shuffleOnce(Supplier<User> user) {
		if (!SHUFFLED.compareAndSet(false, true)) {
			return;
		}

		CapeShuffler.shuffleAsync(user.get());
	}
}
