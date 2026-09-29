package com.example.randomcape;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 一行纯文字的标签，垂直居中。
 *
 * <p>列表里没有现成能用的文字控件：{@code StringWidget} 的文字是贴着控件顶端画的，
 * 摆在滑条旁边会明显偏上、对不齐。所以自己按行高算一下垂直居中。
 */
public class TextLabel extends AbstractWidget {
	private final Font font;

	public TextLabel(Component text, Font font, int height) {
		super(0, 0, 0, height, text);

		this.font = font;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float partialTick) {
		graphics.text(this.font, this.getMessage(), this.getX(),
				this.getY() + (this.getHeight() - this.font.lineHeight) / 2, 0xFFFFFFFF);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
