
package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.renderer.Renderer3D;
import com.gaspoweredcake.client33.utils.render.VisualGeometry;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.combat.KillAura;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.config.Config;
import com.gaspoweredcake.client33.systems.friends.Friends;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.entity.EntityUtils;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.ScreenBounds;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.utils.render.WireframeEntityRenderer;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3d;

import java.util.Set;

public class ESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSignature = settings.createGroup("Signature visuals");
    public final Setting<Boolean> animate = sgSignature.add(new BoolSetting.Builder().name("animate").description("Animate signature ESP scans and orbit markers.").defaultValue(true).build());
    public final Setting<Boolean> throughWalls = sgSignature.add(new BoolSetting.Builder().name("signature-through-walls").description("Show Hologram and Constellation through terrain.").defaultValue(true).build());
    public final Setting<Boolean> groundMarker = sgSignature.add(new BoolSetting.Builder().name("ground-marker").description("Draw a segmented footprint under signature ESP.").defaultValue(true).build());
    public final Setting<Boolean> tether = sgSignature.add(new BoolSetting.Builder().name("target-tether").description("Connect your player to KillAura's selected target in signature modes.").defaultValue(false).build());
    public final Setting<Integer> detail = sgSignature.add(new IntSetting.Builder().name("signature-detail").description("Geometry segments per entity.").defaultValue(32).range(12, 64).sliderRange(12, 64).build());
    public final Setting<Integer> entityBudget = sgSignature.add(new IntSetting.Builder().name("signature-budget").description("Maximum entities drawn per frame in signature modes.").defaultValue(64).range(1, 256).sliderRange(1, 256).build());
    public final Setting<SettingColor> signatureAccent = sgSignature.add(new ColorSetting.Builder().name("signature-accent").description("Secondary scan and telemetry color.").defaultValue(new SettingColor(92, 220, 255, 200)).build());
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Rendering mode.")
        .defaultValue(Mode._2D)
        .build()
    );

    public final Setting<Boolean> highlightTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-target")
        .description("highlights the currently targeted entity differently")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> targetHitbox = sgGeneral.add(new BoolSetting.Builder()
        .name("target-hitbox")
        .description("draw the hitbox of the target entity")
        .defaultValue(false)
        .visible(highlightTarget::get)
        .build()
    );

    public final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("outline-width")
        .description("The width of the shader outline.")
        .visible(() -> mode.get() == Mode.Shader)
        .defaultValue(2)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );

    public final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-multiplier")
        .description("Multiplier for glow effect")
        .visible(() -> mode.get() == Mode.Shader)
        .decimalPlaces(3)
        .defaultValue(1.8)
        .min(0)
        .sliderMax(10)
        .build()
    );

    public final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Ignores yourself drawing the shader.")
        .defaultValue(true)
        .build()
    );

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .visible(() -> mode.get() != Mode.Glow)
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> cornerBox = sgGeneral.add(new BoolSetting.Builder()
        .name("corner-box")
        .description("Draws compact corner brackets instead of a full 2D outline.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode._2D && shapeMode.get() != ShapeMode.Sides)
        .build()
    );

    public final Setting<Double> fillOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .visible(() -> shapeMode.get() != ShapeMode.Lines && mode.get() != Mode.Glow)
        .defaultValue(0.12)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    private final Setting<Double> fadeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fade-distance")
        .description("The distance from an entity where the color begins to fade.")
        .defaultValue(2.5)
        .min(0)
        .sliderMax(12)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Select specific entities.")
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance from the camera for all ESP modes.")
        .defaultValue(128)
        .range(1, 512)
        .sliderRange(16, 256)
        .build()
    );

    private final Setting<Boolean> healthBar = sgGeneral.add(new BoolSetting.Builder()
        .name("health-bar")
        .description("Shows a health bar beside living entities in 2D mode.")
        .defaultValue(true)
        .visible(() -> isScreenMode())
        .build()
    );

    private final Setting<Boolean> distanceLabel = sgGeneral.add(new BoolSetting.Builder()
        .name("distance-label")
        .description("Shows the distance below each 2D box.")
        .defaultValue(true)
        .visible(() -> isScreenMode())
        .build()
    );

    // Colors

    public final Setting<ESPColorMode> colorMode = sgColors.add(new EnumSetting.Builder<ESPColorMode>()
        .name("color-mode")
        .description("Determines the colors used for entities.")
        .defaultValue(ESPColorMode.EntityType)
        .build()
    );

    public final Setting<Boolean> friendOverride = sgColors.add(new BoolSetting.Builder()
        .name("show-friend-colors")
        .description("Whether or not to override the distance/health color of friends with the friend color.")
        .defaultValue(true)
        .visible(() -> colorMode.get() == ESPColorMode.Distance || colorMode.get() == ESPColorMode.Health)
        .build()
    );

    private final Setting<SettingColor> nonLivingEntityColor = sgColors.add(new ColorSetting.Builder()
        .name("non-living-entity-color")
        .description("The color used for non living entities such as dropped items.")
        .defaultValue(new SettingColor(153, 144, 176))
        .visible(() -> colorMode.get() == ESPColorMode.Health)
        .build()
    );

    private final Setting<SettingColor> playersColor = sgColors.add(new ColorSetting.Builder()
        .name("players-color")
        .description("The other player's color.")
        .defaultValue(new SettingColor(157, 111, 255))
        .visible(() -> colorMode.get() == ESPColorMode.EntityType)
        .build()
    );

    private final Setting<SettingColor> animalsColor = sgColors.add(new ColorSetting.Builder()
        .name("animals-color")
        .description("The animal's color.")
        .defaultValue(new SettingColor(103, 224, 181, 255))
        .visible(() -> colorMode.get() == ESPColorMode.EntityType)
        .build()
    );

    private final Setting<SettingColor> waterAnimalsColor = sgColors.add(new ColorSetting.Builder()
        .name("water-animals-color")
        .description("The water animal's color.")
        .defaultValue(new SettingColor(100, 215, 230, 255))
        .visible(() -> colorMode.get() == ESPColorMode.EntityType)
        .build()
    );

    private final Setting<SettingColor> monstersColor = sgColors.add(new ColorSetting.Builder()
        .name("monsters-color")
        .description("The monster's color.")
        .defaultValue(new SettingColor(255, 112, 135, 255))
        .visible(() -> colorMode.get() == ESPColorMode.EntityType)
        .build()
    );

    private final Setting<SettingColor> ambientColor = sgColors.add(new ColorSetting.Builder()
        .name("ambient-color")
        .description("The ambient's color.")
        .defaultValue(new SettingColor(153, 144, 176, 255))
        .visible(() -> colorMode.get() == ESPColorMode.EntityType)
        .build()
    );

    private final Setting<SettingColor> miscColor = sgColors.add(new ColorSetting.Builder()
        .name("misc-color")
        .description("The misc color.")
        .defaultValue(new SettingColor(200, 191, 220, 255))
        .visible(() -> colorMode.get() == ESPColorMode.EntityType)
        .build()
    );

    private final Setting<SettingColor> targetColor = sgColors.add(new ColorSetting.Builder()
        .name("target-color")
        .description("The target color.")
        .defaultValue(new SettingColor(255, 195, 110, 255))
        .visible(highlightTarget::get)
        .build()
    );

    private final Setting<SettingColor> targetHitboxColor = sgColors.add(new ColorSetting.Builder()
        .name("target-hitbox-color")
        .description("The target hitbox color.")
        .defaultValue(new SettingColor(100, 215, 230, 255))
        .visible(() -> highlightTarget.get() && targetHitbox.get())
        .build()
    );

    private final Color lineColor = new Color();
    private final Color sideColor = new Color();
    private final Color baseColor = new Color();

    private final ScreenBounds bounds = new ScreenBounds();
    private final Color healthColor = new Color();
    private final Color healthBackground = new Color(8, 12, 20, 190);
    private final Vector3d pos = new Vector3d();

    private int count;

    public ESP() {
        super(Categories.Render, "esp", "Renders entities through walls.");
    }

    // Box

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (isScreenMode()) return;

        count = 0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (shouldSkip(entity) || getColor(entity) == null) continue;
            if ((mode.get() == Mode.Hologram || mode.get() == Mode.Constellation) && count >= entityBudget.get()) break;
            if (mode.get() == Mode.Hologram || mode.get() == Mode.Constellation) drawSignature(event, entity);
            if ((drawAsTarget(entity) && targetHitbox.get()) || mode.get() == Mode.Box || mode.get() == Mode.Wireframe)
                drawBoundingBox(event, entity);
            count++;
        }
    }

    private void drawBoundingBox(Render3DEvent event, Entity entity) {
        Color color = getColor(entity);
        if (color == null) return;
        lineColor.set(color);
        sideColor.set(color).a((int) (sideColor.a * fillOpacity.get()));

        if (mode.get() == Mode.Wireframe) {
            WireframeEntityRenderer.render(event, entity, 1, sideColor, lineColor, shapeMode.get());
        }

        boolean target = drawAsTarget(entity);

        if (mode.get() == Mode.Box || (targetHitbox.get() && target)) {
            double x = Mth.lerp(event.tickDelta, entity.xOld, entity.getX()) - entity.getX();
            double y = Mth.lerp(event.tickDelta, entity.yOld, entity.getY()) - entity.getY();
            double z = Mth.lerp(event.tickDelta, entity.zOld, entity.getZ()) - entity.getZ();

            ShapeMode shape = shapeMode.get();
            if (target && mode.get() != Mode.Box) shape = ShapeMode.Lines;
            if (target && targetHitbox.get()) lineColor.set(targetHitboxColor.get());

            AABB box = entity.getBoundingBox();
            event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, sideColor, lineColor, shape, 0);
        }
    }

    // 2D

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isScreenMode()) return;

        Renderer2D.COLOR.begin();
        TextRenderer text = TextRenderer.get();
        if (distanceLabel.get()) text.begin(event.graphics, 0.8);
        count = 0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (shouldSkip(entity)) continue;

            AABB box = entity.getBoundingBox();

            double x = Mth.lerp(event.tickDelta, entity.xOld, entity.getX()) - entity.getX();
            double y = Mth.lerp(event.tickDelta, entity.yOld, entity.getY()) - entity.getY();
            double z = Mth.lerp(event.tickDelta, entity.zOld, entity.getZ()) - entity.getZ();

            if (!projectBounds(box, x, y, z)) continue;
            double width = bounds.right - bounds.left;
            double height = bounds.bottom - bounds.top;

            Color color = getColor(entity);
            if (color == null) continue;
            lineColor.set(color);
            sideColor.set(color).a((int) (sideColor.a * fillOpacity.get()));

            if (mode.get() != Mode.Telemetry && shapeMode.get() != ShapeMode.Lines && sideColor.a > 0) {
                Renderer2D.COLOR.quad(bounds.left, bounds.top, width, height, sideColor);
            }
            if (shapeMode.get() != ShapeMode.Sides) {
                if (cornerBox.get() || mode.get() == Mode.Telemetry) {
                    double length = Math.min(12, Math.min(width, height) * 0.28);
                    Renderer2D.COLOR.line(bounds.left, bounds.top, bounds.left + length, bounds.top, lineColor);
                    Renderer2D.COLOR.line(bounds.left, bounds.top, bounds.left, bounds.top + length, lineColor);
                    Renderer2D.COLOR.line(bounds.right, bounds.top, bounds.right - length, bounds.top, lineColor);
                    Renderer2D.COLOR.line(bounds.right, bounds.top, bounds.right, bounds.top + length, lineColor);
                    Renderer2D.COLOR.line(bounds.left, bounds.bottom, bounds.left + length, bounds.bottom, lineColor);
                    Renderer2D.COLOR.line(bounds.left, bounds.bottom, bounds.left, bounds.bottom - length, lineColor);
                    Renderer2D.COLOR.line(bounds.right, bounds.bottom, bounds.right - length, bounds.bottom, lineColor);
                    Renderer2D.COLOR.line(bounds.right, bounds.bottom, bounds.right, bounds.bottom - length, lineColor);
                } else {
                    Renderer2D.COLOR.boxLines(bounds.left, bounds.top, width, height, lineColor);
                }
            }

            if (mode.get() == Mode.Telemetry) {
                double mid = (bounds.left + bounds.right) / 2;
                double scanY = bounds.top + height * (animate.get() ? (Math.sin((entity.tickCount + event.tickDelta) * 0.07) + 1) / 2 : 0.5);
                Renderer2D.COLOR.quad(bounds.left, scanY, width, 1, signatureAccent.get());
                Renderer2D.COLOR.quad(mid - 2, bounds.top - 7, 4, 4, signatureAccent.get());
                for (int tick = 0; tick < 6; tick++) {
                    double ty = bounds.top + height * tick / 5;
                    Renderer2D.COLOR.quad(bounds.right + 4, ty, tick % 5 == 0 ? 7 : 3, 1, lineColor);
                }
            }
            if (healthBar.get() && entity instanceof LivingEntity living && living.getMaxHealth() > 0) {
                double fraction = Mth.clamp(living.getHealth() / living.getMaxHealth(), 0, 1);
                healthColor.set((int) (255 * (1 - fraction)), (int) (220 * fraction), 100, lineColor.a);
                healthBackground.a(lineColor.a * 3 / 4);
                Renderer2D.COLOR.quad(bounds.left - 6, bounds.top, 3, height, healthBackground);
                Renderer2D.COLOR.quad(bounds.left - 6, bounds.bottom - height * fraction, 3, height * fraction, healthColor);
            }
            if (distanceLabel.get()) {
                String label = (mode.get() == Mode.Telemetry ? entity.getName().getString() + " / " : "") + Math.round(Math.sqrt(PlayerUtils.squaredDistanceToCamera(entity.getX(), entity.getY(), entity.getZ()))) + " m";
                double labelX = Mth.clamp((bounds.left + bounds.right - text.getWidth(label)) / 2, 0,
                    Math.max(0, mc.getWindow().getWidth() - text.getWidth(label)));
                double labelY = Math.min(bounds.bottom + 3, mc.getWindow().getHeight() - text.getHeight());
                text.render(label, labelX, labelY, lineColor, true);
            }

            count++;
        }

        Renderer2D.COLOR.render();
        if (distanceLabel.get()) text.end();
    }

    public boolean forceRender() {
        return isActive() && (mode.get() == Mode.Shader || mode.get() == Mode.Glow);
    }

    private boolean projectBounds(AABB box, double x, double y, double z) {
        bounds.reset();
        for (int corner = 0; corner < 8; corner++) {
            pos.set(
                ((corner & 1) == 0 ? box.minX : box.maxX) + x,
                ((corner & 2) == 0 ? box.minY : box.maxY) + y,
                ((corner & 4) == 0 ? box.minZ : box.maxZ) + z
            );
            // Close entities crossing the camera plane are omitted instead of producing giant boxes.
            if (!NametagUtils.to2D(pos, 1, false)) return false;
            bounds.include(pos.x, pos.y, pos.z);
        }
        return bounds.clip(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    // Utils

    public boolean drawAsTarget(Entity entity) {
        if (!highlightTarget.get()) return false;
        KillAura aura = Modules.get().get(KillAura.class);
        return (aura.isActive() && aura.getTarget() == entity)
            || (mc.hitResult instanceof EntityHitResult hr && hr.getEntity() == entity);
    }

    public boolean shouldSkip(Entity entity) {
        if (!entity.isAlive() || entity.isRemoved()) return true;
        if (!drawAsTarget(entity) && !entities.get().contains(entity.getType())) return true;
        if (entity == mc.player && ignoreSelf.get()) return true;
        if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return true;
        return !EntityUtils.isInRenderDistance(entity)
            || PlayerUtils.squaredDistanceToCamera(entity.getX(), entity.getY(), entity.getZ()) > maxDistance.get() * maxDistance.get();
    }

    public boolean shouldSkip(EntityType<?> entityType) {
        return !entities.get().contains(entityType);
    }

    public Color getColor(Entity entity) {
        if (shouldSkip(entity)) return null;
        Color color;
        double alpha = getFadeAlpha(entity);
        if (alpha == 0) return null;

        if (drawAsTarget(entity)) {
            color = targetColor.get();
        } else {
            if (!entities.get().contains(entity.getType())) return null;

            color = getEntityTypeColor(entity);
        }

        return baseColor.set(color.r, color.g, color.b, (int) (color.a * alpha));
    }

    private double getFadeAlpha(Entity entity) {
        double dist = PlayerUtils.squaredDistanceToCamera(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
        double fadeRadius = fadeDistance.get();
        if (fadeRadius <= 0) return 1;
        double alpha = Math.min(1, Math.sqrt(dist) / fadeRadius);
        return alpha <= 0.075 ? 0 : alpha;
    }

    public Color getEntityTypeColor(Entity entity) {
        if (colorMode.get() == ESPColorMode.EntityType) {
            if (entity instanceof Player player) {
                return PlayerUtils.getPlayerColor(player, playersColor.get());
            } else {
                return switch (entity.getType().getCategory()) {
                    case CREATURE -> animalsColor.get();
                    case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> waterAnimalsColor.get();
                    case MONSTER -> monstersColor.get();
                    case AMBIENT -> ambientColor.get();
                    default -> miscColor.get();
                };
            }
        }

        if (friendOverride.get() && entity instanceof Player player
            && Friends.get().isFriend(player)) {
            return Config.get().friendColor.get();
        }

        if (colorMode.get() == ESPColorMode.Health)
            return EntityUtils.getColorFromHealth(entity, nonLivingEntityColor.get());
        else return EntityUtils.getColorFromDistance(entity);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }

    public boolean isShader() {
        return isActive() && mode.get() == Mode.Shader;
    }

    public boolean isGlow() {
        return isActive() && mode.get() == Mode.Glow;
    }

    public enum ESPColorMode {
        EntityType,
        Distance,
        Health;

        @Override
        public String toString() {
            return this == EntityType ? "Entity Type" : super.toString();
        }
    }

    private boolean isScreenMode() {
        return mode.get() == Mode._2D || mode.get() == Mode.Telemetry;
    }

    private void drawSignature(Render3DEvent event, Entity entity) {
        Renderer3D renderer = throughWalls.get() ? event.renderer : event.depthRenderer;
        Color color = getColor(entity);
        if (color == null) return;
        Color accent = new Color(signatureAccent.get()).a((int) (signatureAccent.get().a * getFadeAlpha(entity)));
        double x = Mth.lerp(event.tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(event.tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(event.tickDelta, entity.zOld, entity.getZ());
        double radius = entity.getBbWidth() * 0.7 + 0.15, height = entity.getBbHeight();
        double phase = animate.get() ? (entity.tickCount + event.tickDelta) * 0.045 : 0;
        if (groundMarker.get()) for (int i = 0; i < 4; i++)
            VisualGeometry.ring(renderer, x, y + 0.025, z, radius + 0.12, phase + i * Math.PI / 2, 1.1, detail.get() / 4, color);
        if (mode.get() == Mode.Hologram) {
            double scan = y + height * (0.5 + 0.48 * Math.sin(phase));
            VisualGeometry.ring(renderer, x, scan, z, radius, 0, Math.PI * 2, detail.get(), accent);
            VisualGeometry.band(renderer, x, scan, z, radius, -Math.min(0.45, height * 0.3), detail.get(),
                new Color(accent).a((int) (accent.a * fillOpacity.get())));
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 4 + i * Math.PI / 2;
                double px = x + Math.cos(a) * radius, pz = z + Math.sin(a) * radius;
                renderer.line(px, y, pz, px, y + height, pz, new Color(color).a(color.a / 3));
                renderer.line(px, y + height, pz, x + Math.cos(a) * radius * 0.6, y + height + 0.12, z + Math.sin(a) * radius * 0.6, color);
            }
        } else {
            for (int strand = 0; strand < 2; strand++) {
                for (int i = 0; i < detail.get(); i++) {
                    double t = (double) i / detail.get(), next = (double) (i + 1) / detail.get();
                    double a = phase + t * Math.PI * 2 + strand * Math.PI;
                    double b = phase + next * Math.PI * 2 + strand * Math.PI;
                    double px = x + Math.cos(a) * radius, py = y + t * height, pz = z + Math.sin(a) * radius;
                    renderer.line(px, py, pz, x + Math.cos(b) * radius, y + next * height, z + Math.sin(b) * radius, strand == 0 ? color : accent);
                    if (i % 4 == 0) {
                        renderer.line(px - 0.04, py, pz, px + 0.04, py, pz, accent);
                        renderer.line(px, py - 0.04, pz, px, py + 0.04, pz, accent);
                    }
                }
            }
        }
        KillAura aura = Modules.get().get(KillAura.class);
        if (tether.get() && aura.isActive() && aura.getTarget() == entity)
            renderer.line(Mth.lerp(event.tickDelta, mc.player.xOld, mc.player.getX()), Mth.lerp(event.tickDelta, mc.player.yOld, mc.player.getY()) + 0.1,
                Mth.lerp(event.tickDelta, mc.player.zOld, mc.player.getZ()), x, y + 0.1, z, accent);
    }

    public enum Mode {
        Hologram,
        Constellation,
        Telemetry,
        Box,
        Wireframe,
        _2D,
        Shader,
        Glow;

        @Override
        public String toString() {
            return this == _2D ? "2D" : super.toString();
        }
    }
}
