package com.example.randomcapes;

import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * 配置界面。
 *
 * <p>直接继承原版的 {@link OptionsSubScreen}：顶部标题、可滚动的选项列表、底部的
 * 「完成」按钮，全部由基类提供。所以外观和原版选项界面完全一致，不需要自己摆控件，
 * 也自动支持窄屏分两列。
 *
 * <p>选项本身也按双列排（{@code addSmall}），一屏能看全，不用滚。
 */
public class RandomCapesConfigScreen extends OptionsSubScreen {
	/**
	 * 「随机披风」是总开关。它关着的时候，另外三个披风相关的选项就没有作用对象了，
	 * 一起置灰 —— 这里留着它们的引用来切换可用状态。
	 */
	private AbstractWidget modeButton;
	private AbstractWidget weightedButton;
	private AbstractWidget weightsButton;

	public RandomCapesConfigScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options,
				Component.translatable("randomcapes.config.title"));
	}

	@Override
	protected void addOptions() {
		RandomCapesConfig config = RandomCapesConfig.get();

		// 第一行：随机披风 | 随机模式
		// 开关得先用 createButton 变成普通控件 —— addSmall 没有「OptionInstance + 别的控件」
		// 混搭的重载，只有「两个 OptionInstance」或「两个 AbstractWidget」。
		// 另外后三个要留着引用才能置灰，所以不直接把 OptionInstance 丢给 addSmall。
		this.modeButton = modeOption(config).createButton(this.minecraft.options);

		this.list.addSmall(
				switchOption("randomcapes.config.enabled", config.enabled,
						value -> config.enabled = value, this::refreshCapeControls)
						.createButton(this.minecraft.options),
				this.modeButton);

		// 第二行：抽选跟随权重 | 披风权重（点进去调）
		// 权重开关一动，「披风权重」的可用状态也得跟着刷新。
		this.weightedButton = switchOption("randomcapes.config.weighted", config.weightedEnabled,
						value -> config.weightedEnabled = value, this::refreshCapeControls)
				.createButton(this.minecraft.options);

		this.weightsButton = Button.builder(Component.translatable("randomcapes.config.cape_weights"),
						button -> this.minecraft.setScreenAndShow(new CapeWeightsScreen(this)))
				.tooltip(Tooltip.create(
						Component.translatable("randomcapes.config.cape_weights.tooltip")))
				.build();

		this.list.addSmall(this.weightedButton, this.weightsButton);

		// 界面刚建出来时也要对上当前的主开关状态
		refreshCapeControls();
	}

	/** 一个开/关选项：说明放在悬浮提示里。值一改就落盘。 */
	private static OptionInstance<Boolean> switchOption(String key, boolean initial, SwitchSetter setter) {
		return switchOption(key, initial, setter, () -> {
		});
	}

	/** 同上，另外在值变化之后再跑一段收尾逻辑。 */
	private static OptionInstance<Boolean> switchOption(String key, boolean initial, SwitchSetter setter,
														Runnable afterChange) {
		return OptionInstance.createBoolean(
				key,
				value -> Tooltip.create(Component.translatable(key + ".tooltip")),
				initial,
				value -> {
					setter.set(value);
					RandomCapesConfig.get().save();
					afterChange.run();
				});
	}

	/**
	 * 两个开关都会影响别的控件能不能用：
	 *
	 * <ul>
	 *   <li>「随机披风」关掉时，另外三个都没有作用对象了，一起置灰</li>
	 *   <li>「抽选跟随权重」关掉时，权重表就没意义了，「披风权重」单独置灰 ——
	 *       只是点不进去而已，里面存着的权重不会丢，下次再打开还在</li>
	 * </ul>
	 */
	private void refreshCapeControls() {
		RandomCapesConfig config = RandomCapesConfig.get();
		boolean enabled = config.enabled;

		setActive(this.modeButton, enabled);
		setActive(this.weightedButton, enabled);
		setActive(this.weightsButton, enabled && config.weightedEnabled);
	}

	private static void setActive(AbstractWidget widget, boolean active) {
		if (widget != null) {
			widget.active = active;
		}
	}

	/** 随机模式。它的说明要跟着当前选中的模式变，所以 tooltip 是按值现算的。 */
	private static OptionInstance<RandomCapesConfig.ShuffleMode> modeOption(RandomCapesConfig config) {
		return new OptionInstance<>(
				"randomcapes.config.shuffle_mode",
				mode -> Tooltip.create(Component.translatable(modeKey("tooltip", mode))),
				(caption, mode) -> Component.translatable(modeKey("name", mode)),
				new OptionInstance.Enum<>(List.of(RandomCapesConfig.ShuffleMode.values()),
						Codec.STRING.xmap(RandomCapesConfig.ShuffleMode::valueOf, Enum::name)),
				config.shuffleMode,
				mode -> {
					config.shuffleMode = mode;
					config.save();
				});
	}

	/** 拼出形如 {@code randomcapes.shuffle_mode.name.pseudo_random} 的翻译键。 */
	private static String modeKey(String kind, RandomCapesConfig.ShuffleMode mode) {
		return "randomcapes.shuffle_mode." + kind + "." + mode.name().toLowerCase(Locale.ROOT);
	}

	/** 开关改了往哪儿写。 */
	@FunctionalInterface
	private interface SwitchSetter {
		void set(boolean value);
	}
}
