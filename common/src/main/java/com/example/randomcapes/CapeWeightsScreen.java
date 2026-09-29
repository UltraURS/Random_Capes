package com.example.randomcapes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 权重界面：每个披风一条 0~1 的滑条，双列排。
 *
 * <p>披风列表来自 {@link CapeShuffler#knownCapes()} —— 也就是启动时那次网络请求
 * 顺手缓存下来的结果。读不到 profile（离线账号、没连上网）时列表是空的，界面给出说明。
 */
public class CapeWeightsScreen extends OptionsSubScreen {
	private static final int FOOTER_BUTTON_WIDTH = 100;

	/** 每条滑条。底部「重置」要挨个把它们推回默认权重。 */
	private final List<OptionInstance<Double>> weightOptions = new ArrayList<>();

	public CapeWeightsScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options,
				Component.translatable("randomcapes.weights.title"));
	}

	@Override
	protected void addOptions() {
		this.weightOptions.clear();

		List<CapeShuffler.Cape> capes = CapeShuffler.knownCapes();

		if (capes.isEmpty()) {
			// 离线账号 / 请求失败 —— 说清楚为什么这里是空的，别让玩家以为界面坏了
			this.list.addBig(new TextLabel(Component.translatable("randomcapes.weights.empty"),
					this.font, 20));
			this.list.addBig(new TextLabel(Component.translatable("randomcapes.weights.empty_hint"),
					this.font, 20));
			return;
		}

		RandomCapesConfig config = RandomCapesConfig.get();

		for (CapeShuffler.Cape cape : capes) {
			this.weightOptions.add(weightOption(cape, config));
		}

		// 双列：一屏能多看几个披风
		for (int i = 0; i < this.weightOptions.size(); i += 2) {
			if (i + 1 < this.weightOptions.size()) {
				this.list.addSmall(this.weightOptions.get(i), this.weightOptions.get(i + 1));
			} else {
				this.list.addSmall(this.weightOptions.get(i));
			}
		}
	}

	/**
	 * 底部按钮：原版这里只摆一个 200 宽的「完成」，换成「重置 + 完成」并排。
	 *
	 * <p>用一个横向 {@link LinearLayout} 把两个按钮捆成一行再交给页脚居中，
	 * 比自己算 x 坐标稳（窗口尺寸变了也不会错位）。
	 */
	@Override
	protected void addFooter() {
		Button reset = Button.builder(
						Component.translatable("randomcapes.weights.reset"),
						button -> this.resetWeights())
				.width(FOOTER_BUTTON_WIDTH)
				.tooltip(Tooltip.create(
						Component.translatable("randomcapes.weights.reset.tooltip")))
				.build();

		// 列表都没读到时没有什么可重置的
		reset.active = !this.weightOptions.isEmpty();

		LinearLayout row = LinearLayout.horizontal().spacing(8);
		row.addChild(reset);
		row.addChild(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
				.width(FOOTER_BUTTON_WIDTH)
				.build());

		this.layout.addToFooter(row);
	}

	/**
	 * 全部推回 {@link RandomCapesConfig#DEFAULT_CAPE_WEIGHT}。
	 *
	 * <p>改完配置必须**重建界面**才看得见：滑条上那行「名字: 0.00」是控件创建时算好的，
	 * 直接改 {@code OptionInstance} 的值并不会让它重画（实测：值已经是 0.0，界面上仍写着
	 * 1.00 —— 玩家会以为重置没生效）。重建一次最省事，顺带也让滑条重新对齐到当前配置。
	 */
	private void resetWeights() {
		RandomCapesConfig config = RandomCapesConfig.get();

		for (CapeShuffler.Cape cape : CapeShuffler.knownCapes()) {
			config.capeWeights.put(cape.id(), RandomCapesConfig.DEFAULT_CAPE_WEIGHT);
		}

		config.save();

		this.minecraft.setScreenAndShow(new CapeWeightsScreen(this.lastScreen));
	}

	/** 一个披风对应一条滑条。值一改就落盘。 */
	private static OptionInstance<Double> weightOption(CapeShuffler.Cape cape, RandomCapesConfig config) {
		return new OptionInstance<>(
				// 披风名不是翻译键，TranslatableContents 找不到就会原样显示，正好
				cape.alias(),
				OptionInstance.noTooltip(),
				// caption 是选项的标签文本，必须自己拼进去 —— 直接返回数字的话，
				// 滑条上就只剩一个光秃秃的 "1.00"，根本看不出这是哪个披风
				(caption, value) -> caption.copy()
						.append(": ")
						.append(String.format(Locale.ROOT, "%.2f", value)),
				OptionInstance.UnitDouble.INSTANCE,
				config.weightOf(cape.id()),
				value -> {
					config.capeWeights.put(cape.id(), RandomCapesConfig.clampWeight(value));
					config.save();
				});
	}
}
