package com.maguet.armorcemcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.features.property_reading.properties.RandomProperties;

/**
 * Hace que los packs de armadura 3D (CEM) dejen de romper la armadura de los mods.
 *
 * Un modelo CEM de armadura se aplica a la capa de armadura de la ENTIDAD, no al item, así
 * que también captura las armaduras de otros mods — que usan el layout vanilla de 64×32
 * mientras el pack suele usar 64×64. Resultado: armadura deformada. Y EMF no ofrecía forma
 * de filtrarlo: su propiedad `items` llega vacía en las capas de armadura.
 *
 * Este mod aporta dos cosas:
 *   1. La propiedad `armor_item` para los .properties, que sí lee la armadura equipada.
 *   2. Un resource pack integrado con las reglas ya escritas, para que funcione sin
 *      configurar nada. Se puede desactivar si prefieres escribir las tuyas.
 */
public class ArmorCemCompat implements ClientModInitializer {

    public static final String MOD_ID = "armorcemcompat";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        registrarPropiedad();
        registrarPackIntegrado();
    }

    /** Añade `armor_item` al motor de reglas de ETF. */
    private static void registrarPropiedad() {
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

    /**
     * Resource pack integrado con las reglas de compatibilidad, activado por defecto.
     *
     * Solo AÑADE archivos que ningún pack de armadura trae (`<modelo>2.jem` y su
     * `.properties`), así que se puede cargar junto a cualquier pack sin sobrescribirle nada:
     * el pack del autor queda intacto y su modelo 3D sigue siendo la variante 1.
     *
     * Se deja DESACTIVABLE a propósito: si algún día el pack de armadura trae sus propias
     * reglas, las nuestras las pisarían al ir por encima. En ese caso el usuario apaga este
     * pack y conserva la propiedad `armor_item`, que las reglas del autor siguen necesitando.
     */
    private static void registrarPackIntegrado() {
        try {
            FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container ->
                    ResourceManagerHelper.registerBuiltinResourcePack(
                            Identifier.fromNamespaceAndPath(MOD_ID, "compat"),
                            container,
                            ResourcePackActivationType.DEFAULT_ENABLED
                    ));
            LOGGER.info("[ArmorCemCompat] pack de compatibilidad integrado registrado");
        } catch (Throwable t) {
            LOGGER.error("[ArmorCemCompat] no se pudo registrar el pack integrado", t);
        }
    }
}
