package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.entity.player.AttackEntityEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.renderer.Renderer3D;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.combat.KillAura;
import com.gaspoweredcake.client33.utils.render.VisualGeometry;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

public class CombatVisuals extends Module {
    private final SettingGroup sgTarget = settings.createGroup("Target choreography");
    private final SettingGroup sgEffects = settings.createGroup("Attack & motion");
    private final SettingGroup sgAtmosphere = settings.createGroup("Euphoric atmosphere");
    private final SettingGroup sgCamera = settings.createGroup("Camera & performance");
    public final Setting<Boolean> targetOrbit = sgTarget.add(new BoolSetting.Builder().name("target-orbit").description("Rotating broken rings around KillAura's selected target.").defaultValue(true).build());
    public final Setting<Boolean> scanBand = sgTarget.add(new BoolSetting.Builder().name("scan-band").description("A luminous band sweeps up and down the target.").defaultValue(true).build());
    public final Setting<Boolean> crown = sgTarget.add(new BoolSetting.Builder().name("target-crown").description("Floating diamond markers over the selected target.").defaultValue(true).build());
    public final Setting<Double> speed = sgTarget.add(new DoubleSetting.Builder().name("animation-speed").description("Orbit and scan animation speed; zero freezes target animation.").defaultValue(1).range(0, 3).build());
    public final Setting<SettingColor> primary = sgTarget.add(new ColorSetting.Builder().name("primary-color").description("Main orbit and burst color.").defaultValue(new SettingColor(255, 65, 95, 220)).build());
    public final Setting<SettingColor> secondary = sgTarget.add(new ColorSetting.Builder().name("secondary-color").description("Secondary rings, trails, and sparks.").defaultValue(new SettingColor(92, 220, 255, 200)).build());
    public final Setting<Boolean> attackBursts = sgEffects.add(new BoolSetting.Builder().name("attack-bursts").description("Expanding rings on local attack attempts; these do not confirm server damage.").defaultValue(true).build());
    public final Setting<Boolean> sparks = sgEffects.add(new BoolSetting.Builder().name("sparks").description("Radial shards accompany attack bursts.").defaultValue(true).build());
    public final Setting<Boolean> trails = sgEffects.add(new BoolSetting.Builder().name("movement-trail").description("A fading ribbon follows your movement during KillAura.").defaultValue(true).build());
    public final Setting<Double> lifetime = sgEffects.add(new DoubleSetting.Builder().name("effect-lifetime").description("Attack effect duration in seconds.").defaultValue(0.65).range(0.15, 2).build());
    public final Setting<Double> size = sgEffects.add(new DoubleSetting.Builder().name("effect-size").description("Attack burst radius multiplier.").defaultValue(1).range(0.3, 3).build());
    public final Setting<Boolean> thirdPerson = sgCamera.add(new BoolSetting.Builder().name("auto-third-person").description("Use rear third person while KillAura is active. Manual perspective changes take priority until the next activation.").defaultValue(false).build());
    public final Setting<Boolean> throughWalls = sgCamera.add(new BoolSetting.Builder().name("through-walls").description("Show combat effects through terrain.").defaultValue(false).build());
    public final Setting<Integer> detail = sgCamera.add(new IntSetting.Builder().name("detail").description("Segments per ring; lower values reduce geometry.").defaultValue(48).range(16, 96).sliderRange(16, 96).build());
    public final Setting<Integer> maxBursts = sgCamera.add(new IntSetting.Builder().name("max-bursts").description("Maximum simultaneous attack effects.").defaultValue(12).range(1, 32).sliderRange(1, 32).build());

    public final Setting<Boolean> euphoricAura = sgAtmosphere.add(new BoolSetting.Builder().name("euphoric-aura").description("Layered spectral ribbons and orbit halos around the combat target.").defaultValue(true).build());
    public final Setting<Double> intensity = sgAtmosphere.add(new DoubleSetting.Builder().name("intensity").description("Opacity of spectral ribbons and halos; zero removes the atmosphere.").defaultValue(0.65).range(0, 1).build());
    public final Setting<Boolean> manualTarget = sgAtmosphere.add(new BoolSetting.Builder().name("manual-target").description("Show target effects on your last manually attacked player too.").defaultValue(true).build());

    private final ArrayDeque<Burst> bursts = new ArrayDeque<>();
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private CameraType previousCamera;
    private boolean cameraOverridden;
    private Object world;
    private double clock;
    private record Burst(Vec3 position, double born, double duration) {}

    public CombatVisuals() {
        super(Categories.Render, "combat-visuals", "Animated target rings, attack bursts, motion ribbons and a third-person combat camera.");
    }

    @Override public void onActivate() { reset(); }
    @Override public void onDeactivate() { reset(); }
    @EventHandler private void onLeave(GameLeftEvent event) { reset(); }

    private boolean otherCamera() {
        return Modules.get().isActive(Freecam.class) || Modules.get().isActive(FreeLook.class);
    }

    private void releaseCamera() {
        if (previousCamera != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && !otherCamera())
            mc.options.setCameraType(previousCamera);
        previousCamera = null;
    }

    private void reset() {
        releaseCamera();
        cameraOverridden = false;
        bursts.clear();
        trail.clear();
        clock = 0;
        world = mc.level;
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (world != mc.level) reset();
        if (mc.player == null) return;
        clock += 0.05;
        bursts.removeIf(b -> clock - b.born >= b.duration);
        boolean aura = Modules.get().isActive(KillAura.class);
        if (!aura || !thirdPerson.get()) {
            releaseCamera();
            cameraOverridden = false;
        } else if (otherCamera()) {
            // The other module now owns the camera. Do not restore over its saved perspective.
            previousCamera = null;
            cameraOverridden = true;
        } else if (previousCamera != null && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            previousCamera = null;
            cameraOverridden = true;
        } else if (previousCamera == null && !cameraOverridden) {
            previousCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (aura && trails.get()) {
            Vec3 position = mc.player.position();
            if (!trail.isEmpty() && trail.getLast().distanceToSqr(position) > 64) trail.clear();
            trail.addLast(position);
            while (trail.size() > 24) trail.removeFirst();
        } else trail.clear();
    }

    @EventHandler(priority = -1000)
    private void onAttack(AttackEntityEvent event) {
        if (event.isCancelled() || !attackBursts.get() || mc.player == null || mc.level == null) return;
        if (world != mc.level) reset();
        while (bursts.size() >= maxBursts.get()) bursts.removeFirst();
        bursts.addLast(new Burst(event.entity.position().add(0, event.entity.getBbHeight() * 0.45, 0), clock, lifetime.get()));
    }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || world != mc.level) return;
        Renderer3D renderer = throughWalls.get() ? event.renderer : event.depthRenderer;
        double time = clock + event.tickDelta * 0.05;
        KillAura aura = Modules.get().get(KillAura.class);
        Entity target = aura.isActive() ? aura.getTarget() : manualTarget.get() ? com.gaspoweredcake.client33.utils.combat.DuelTelemetry.target() : null;
        if (target != null && target.isAlive() && !target.isRemoved()) renderTarget(renderer, target, event.tickDelta, time);
        if (attackBursts.get()) for (Burst burst : bursts) {
            double p = VisualGeometry.progress(time - burst.born, burst.duration);
            Color color = new Color(primary.get()).a((int) (primary.get().a * (1 - p)));
            Vec3 at = burst.position;
            double radius = (0.2 + p * 1.8) * size.get();
            VisualGeometry.ring(renderer, at.x, at.y, at.z, radius, 0, Math.PI * 2, detail.get(), color);
            VisualGeometry.band(renderer, at.x, at.y, at.z, radius, 0.22 * (1 - p), detail.get(), color);
            if (sparks.get()) for (int i = 0; i < 10; i++) {
                double angle = i * Math.PI / 5;
                double y = at.y + Math.sin(p * Math.PI) * (0.5 + (i % 3) * 0.2);
                renderer.line(at.x + Math.cos(angle) * radius, y, at.z + Math.sin(angle) * radius,
                    at.x + Math.cos(angle) * (radius + 0.16), y + 0.15 * (1 - p), at.z + Math.sin(angle) * (radius + 0.16), color);
            }
        }
        if (trails.get() && !mc.options.getCameraType().isFirstPerson()) {
            Vec3 previous = null;
            int index = 0;
            for (Vec3 point : trail) {
                Color color = new Color(secondary.get()).a(secondary.get().a * index++ / Math.max(1, trail.size()));
                if (previous != null) renderer.quad(previous.x, previous.y + 0.08, previous.z,
                    point.x, point.y + 0.08, point.z, point.x, point.y + 0.45, point.z,
                    previous.x, previous.y + 0.45, previous.z, color);
                previous = point;
            }
        }
    }

    private void renderTarget(Renderer3D renderer, Entity target, float delta, double time) {
        double x = Mth.lerp(delta, target.xOld, target.getX());
        double y = Mth.lerp(delta, target.yOld, target.getY());
        double z = Mth.lerp(delta, target.zOld, target.getZ());
        double radius = target.getBbWidth() * 0.7 + 0.25;
        double phase = time * speed.get() * 2;
        if (euphoricAura.get() && intensity.get() > 0) renderAtmosphere(renderer, x, y, z, radius, target.getBbHeight(), phase);
        if (targetOrbit.get()) for (int i = 0; i < 3; i++) {
            VisualGeometry.ring(renderer, x, y + 0.05, z, radius, phase + i * Math.PI * 2 / 3, 1.55, detail.get() / 3, primary.get());
            VisualGeometry.ring(renderer, x, y + 0.09, z, radius + 0.18, -phase + i * Math.PI * 2 / 3, 0.9, detail.get() / 3, secondary.get());
        }
        if (scanBand.get()) {
            double scan = y + (0.5 + Math.sin(phase) * 0.45) * target.getBbHeight();
            VisualGeometry.band(renderer, x, scan, z, radius, -0.35, detail.get(), new Color(primary.get()).a(primary.get().a / 2));
            VisualGeometry.ring(renderer, x, scan, z, radius, 0, Math.PI * 2, detail.get(), primary.get());
        }
        if (crown.get()) for (int i = 0; i < 3; i++) {
            double a = phase + i * Math.PI * 2 / 3;
            double cx = x + Math.cos(a) * radius * 0.7, cz = z + Math.sin(a) * radius * 0.7;
            double cy = y + target.getBbHeight() + 0.3 + Math.sin(phase) * 0.08;
            renderer.line(cx - 0.08, cy, cz, cx, cy + 0.15, cz, secondary.get());
            renderer.line(cx, cy + 0.15, cz, cx + 0.08, cy, cz, secondary.get());
            renderer.line(cx + 0.08, cy, cz, cx, cy - 0.15, cz, secondary.get());
            renderer.line(cx, cy - 0.15, cz, cx - 0.08, cy, cz, secondary.get());
        }
    }
    private void renderAtmosphere(Renderer3D renderer, double x, double y, double z, double radius, double height, double phase) {
        int segments = detail.get();
        for (int band = 0; band < 2; band++) for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments + phase * (band == 0 ? 0.6 : -0.4);
            double b = (i + 1) * Math.PI * 2 / segments + phase * (band == 0 ? 0.6 : -0.4);
            double r = radius + 0.12 + band * 0.17;
            double ya = y + height * (0.45 + 0.25 * Math.sin(a * 2 + phase + band * Math.PI));
            double yb = y + height * (0.45 + 0.25 * Math.sin(b * 2 + phase + band * Math.PI));
            Color color = com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(i / (double) segments + phase * 0.015, (int) (160 * intensity.get()));
            Color fade = new Color(color).a(0);
            double ax = x + Math.cos(a) * r, az = z + Math.sin(a) * r;
            double bx = x + Math.cos(b) * r, bz = z + Math.sin(b) * r;
            renderer.line(ax, ya, az, bx, yb, bz, color);
            renderer.quad(ax, ya, az, bx, yb, bz, bx, yb - 0.22, bz, ax, ya - 0.22, az, color, color, fade, fade);
            if (band == 0) renderer.line(ax, y + 0.03, az, bx, y + 0.03, bz, new Color(color).a(color.a / 2));
        }
    }

}
