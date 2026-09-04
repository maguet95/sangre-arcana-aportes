package com.maguet.armorcemcompat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import traben.entity_texture_features.features.property_reading.properties.RandomProperty;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.utils.ETFEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Propiedad "armor_item" para los .properties de EMF/ETF.
 *
 * Existe porque la propiedad "items" de ETF llega vacía al evaluar las capas de
 * armadura, y la vía por NBT es cara y se queda desincronizada. Esta lee el item
 * del slot directamente de la entidad viva: sin NBT, sin caché, sin serializar nada.
 *
 * Sintaxis:
 *   armor_item.1=chest:minecraft:*_chestplate
 *   armor_item.1=head:minecraft:*_helmet head:minecraft:turtle_helmet
 *   armor_item.1=chest:!advancednetherite:*
 *
 * Formato de cada patrón:  <slot>:<id con comodines>
 *   slot   = head | chest | legs | feet   (también helmet/chestplate/leggings/boots)
 *   id     = admite '*'; si se omite el namespace se asume "minecraft"
 *   '!'    = niega ese patrón
 *
 * La regla se cumple si CUALQUIERA de los patrones positivos coincide, y NINGUNO
 * de los negados coincide.
 */
public class ArmorItemProperty extends RandomProperty {

    /** Nombres aceptados en el .properties. */
    public static final String[] IDS = {"armor_item", "armor_items"};

    private final List<Rule> positives;
    private final List<Rule> negatives;
    private final String printable;

    private record Rule(EquipmentSlot slot, Pattern pattern) {}

    private ArmorItemProperty(List<Rule> positives, List<Rule> negatives, String printable) {
        this.positives = positives;
        this.negatives = negatives;
        this.printable = printable;
        // Imprescindible: sin esto ETF cachea el primer resultado por entidad y la
        // regla nunca se vuelve a evaluar al cambiarse de armadura.
        setCanUpdate(true);
    }

    /** Fábrica que ETF invoca por cada regla numerada del .properties. */
    public static RandomProperty getPropertyOrNull(Properties properties, int ruleNumber) {
        try {
            String raw = RandomProperty.readPropertiesOrThrow(properties, ruleNumber, IDS);
            if (raw == null || raw.isBlank()) return null;

            List<Rule> positives = new ArrayList<>();
            List<Rule> negatives = new ArrayList<>();

            for (String token : raw.trim().split("\\s+")) {
                if (token.isBlank()) continue;
                boolean negated = token.startsWith("!");
                if (negated) token = token.substring(1);

                int sep = token.indexOf(':');
                if (sep <= 0) continue;                       // sin slot declarado: se ignora
                EquipmentSlot slot = parseSlot(token.substring(0, sep));
                if (slot == null) continue;

                String id = token.substring(sep + 1);
                if (id.isBlank()) continue;
                if (!id.contains(":")) id = "minecraft:" + id;  // namespace por defecto

                Rule rule = new Rule(slot, toPattern(id));
                (negated ? negatives : positives).add(rule);
            }

            if (positives.isEmpty() && negatives.isEmpty()) return null;
            return new ArmorItemProperty(positives, negatives, raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static EquipmentSlot parseSlot(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "head", "helmet"          -> EquipmentSlot.HEAD;
            case "chest", "chestplate"     -> EquipmentSlot.CHEST;
            case "legs", "leggings"        -> EquipmentSlot.LEGS;
            case "feet", "boots"           -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    /** Convierte un id con '*' en una expresión regular, escapando el resto. */
    private static Pattern toPattern(String id) {
        StringBuilder sb = new StringBuilder();
        for (String piece : id.split("\\*", -1)) {
            if (sb.length() > 0) sb.append(".*");
            sb.append(Pattern.quote(piece));
        }
        return Pattern.compile(sb.toString());
    }

    @Override
    protected boolean testEntityInternal(ETFEntityRenderState state) {
        ETFEntity etfEntity = state.entity();
        // ETFEntity lo implementa la propia Entity de Minecraft (mixin), así que
        // el cast llega a la entidad real sin pasar por el estado precomputado.
        if (!(etfEntity instanceof LivingEntity living)) {
            return false;
        }


        for (Rule rule : negatives) {
            if (matches(living, rule)) return false;
        }
        if (positives.isEmpty()) return true;   // solo había negaciones
        for (Rule rule : positives) {
            if (matches(living, rule)) return true;
        }
        return false;
    }

    private static boolean matches(LivingEntity living, Rule rule) {
        ItemStack stack = living.getItemBySlot(rule.slot());
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());   // var: evita depender del paquete de ResourceLocation
        return id != null && rule.pattern().matcher(id.toString()).matches();
    }

    @Override
    public String[] getPropertyIds() {
        return IDS;
    }

    @Override
    protected String getPrintableRuleInfo() {
        return "armor_item=" + printable;
    }
}
