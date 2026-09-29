package com.example.randomcapes.fabric;

import com.example.randomcapes.RandomCapes;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Fabric 这一侧的入口。
 *
 * <p>只干一件事：客户端起来之后把真实体所在的 {@link RandomCapes#shuffleOnce} 调一次。
 * Fabric 有现成的「客户端启动完成」事件，比挂 tick 干净，所以这里直接用它。
 */
public class RandomCapesFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> RandomCapes.shuffleOnce(client::getUser));
	}
}
