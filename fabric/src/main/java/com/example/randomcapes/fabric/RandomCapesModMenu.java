package com.example.randomcapes.fabric;

import com.example.randomcapes.RandomCapesConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Only loaded when Mod Menu is present, which is what puts the "Config" button
 * next to this mod in the mod list.
 */
public class RandomCapesModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return RandomCapesConfigScreen::new;
	}
}
