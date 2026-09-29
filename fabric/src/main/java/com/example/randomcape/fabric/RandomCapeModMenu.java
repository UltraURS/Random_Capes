package com.example.randomcape.fabric;

import com.example.randomcape.RandomCapeConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Only loaded when Mod Menu is present, which is what puts the "Config" button
 * next to this mod in the mod list.
 */
public class RandomCapeModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return RandomCapeConfigScreen::new;
	}
}
