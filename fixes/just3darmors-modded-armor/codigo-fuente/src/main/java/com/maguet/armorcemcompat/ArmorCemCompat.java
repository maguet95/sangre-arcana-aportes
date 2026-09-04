package com.maguet.armorcemcompat;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.features.property_reading.properties.RandomProperties;

/**
 * Registra en ETF la propiedad "armor_item", que permite a un .properties de EMF
 * decidir el modelo según la armadura realmente equipada.
 *
 * Sin esto, un resource pack de armadura 3D (CEM) se aplica a TODA armadura, incluida
 * la de otros mods, que usa un mapa de textura distinto y sale deformada.
 */
public class ArmorCemCompat implements ClientModInitializer {

    public static final String MOD_ID = "armorcemcompat";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        try {
            ETFApi.registerCustomRandomPropertyFactory(
                    MOD_ID,
                    RandomProperties.RandomPropertyFactory.of(
                            "armor_item",
                            MOD_ID + ".armor_item_property",
                            ArmorItemProperty::getPropertyOrNull,
                            true   // updatesOverTime: se re-evalúa al cambiar de armadura
                    )
            );
            LOGGER.info("[ArmorCemCompat] propiedad 'armor_item' registrada en ETF");
        } catch (Throwable t) {
            // Si ETF cambia su API, el modpack no debe caerse por esto.
            LOGGER.error("[ArmorCemCompat] no se pudo registrar la propiedad 'armor_item'", t);
        }
    }
}
