package com.example.randomcape.neoforge;

import com.example.randomcape.RandomCape;
import com.example.randomcape.RandomCapeConfigScreen;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge 这一侧的入口。
 *
 * <p>{@code dist = Dist.CLIENT} 是必须的：本模组从头到尾都是客户端代码（要读
 * {@link Minecraft#getInstance()}，服务端根本没有这个类），不限定的话在专用服务器上
 * 加载会直接崩。
 */
@Mod(value = RandomCape.MOD_ID, dist = Dist.CLIENT)
public final class RandomCapeNeoForge {
	public RandomCapeNeoForge(ModContainer container) {
		// 模组列表里的「Config」按钮。作用等同于 Fabric 那边的 Mod Menu 入口。
		//
		// 必须先有个明确类型再传进去：ModContainer 上同时有
		// registerExtensionPoint(Class<T>, T) 和 (Class<T>, Supplier<T>) 两个重载，
		// 直接写 lambda 编译器会判不出来该用哪个（报错是「对 registerExtensionPoint 的引用不明确」）。
		IConfigScreenFactory factory = (modContainer, parent) -> new RandomCapeConfigScreen(parent);

		container.registerExtensionPoint(IConfigScreenFactory.class, factory);

		NeoForge.EVENT_BUS.addListener(this::onClientTick);
	}

	/**
	 * 触发时机挂的是 tick 而不是某个「客户端就绪」事件：这一个 import 就能覆盖所有版本，
	 * 不必再去猜它这一版到底叫什么。重复调用由 {@link RandomCape#shuffleOnce} 挡掉。
	 */
	private void onClientTick(ClientTickEvent.Post event) {
		RandomCape.shuffleOnce(() -> Minecraft.getInstance().getUser());
	}
}
