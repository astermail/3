/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.Zoom;
import com.gaspoweredcake.client33.utils.Utils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.*;

import static com.gaspoweredcake.client33.Client33.mc;

public class NametagUtils {
    private static final Vector4f vec4 = new Vector4f();
    private static final Vector4f mmMat4 = new Vector4f();
    private static final Vector4f pmMat4 = new Vector4f();
    private static final Vector3d camera = new Vector3d();
    private static final Vector3d cameraNegated = new Vector3d();
    private static final Matrix4f model = new Matrix4f();
    private static final Matrix4f projection = new Matrix4f();
    private static double windowScale;

    public static double scale;

    private NametagUtils() {
    }

    public static void onRender(Matrix4fc modelView) {
        model.set(modelView);
        NametagUtils.projection.set(RenderUtils.projection);

        Utils.set(camera, mc.gameRenderer.mainCamera().position());
        cameraNegated.set(camera);
        cameraNegated.negate();

        windowScale = mc.getWindow().calculateScale(1, false);
    }

    public static boolean to2D(Vector3d pos, double scale) {
        return to2D(pos, scale, true);
    }

    public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling) {
        return to2D(pos, scale, distanceScaling, false);
    }

    public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling, boolean allowBehind) {
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)
            || !Double.isFinite(scale) || scale <= 0) return false;
        Zoom zoom = Modules.get().get(Zoom.class);
        NametagUtils.scale = scale * zoom.getScaling();
        if (distanceScaling) {
            NametagUtils.scale *= getScale(pos);
        }

        vec4.set(cameraNegated.x + pos.x, cameraNegated.y + pos.y, cameraNegated.z + pos.z, 1);

        vec4.mul(model, mmMat4);
        mmMat4.mul(projection, pmMat4);

        return ScreenProjection.project(pmMat4.x, pmMat4.y, pmMat4.z, pmMat4.w,
            mc.getWindow().getWidth() / windowScale, mc.getWindow().getHeight() / windowScale, allowBehind, pos);
    }

    public static void begin(Vector3d pos) {
        Matrix4fStack matrices = RenderSystem.getModelViewStack();
        begin(matrices, pos);
    }

    public static void begin(Vector3d pos, GuiGraphicsExtractor graphics) {
        begin(pos);

        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.scale(1.0f / mc.getWindow().getGuiScale());
        matrices.translate((float) pos.x, (float) pos.y);
        matrices.scale((float) scale, (float) scale);
    }

    private static void begin(Matrix4fStack matrices, Vector3d pos) {
        matrices.pushMatrix();
        matrices.translate((float) pos.x, (float) pos.y, 0);
        matrices.scale((float) scale, (float) scale, 1);
    }

    public static void end() {
        RenderSystem.getModelViewStack().popMatrix();
    }

    public static void end(GuiGraphicsExtractor graphics) {
        end();
        graphics.pose().popMatrix();
    }

    private static double getScale(Vector3d pos) {
        double dist = camera.distance(pos);
        return Mth.clamp(1 - dist * 0.01, 0.5, Integer.MAX_VALUE);
    }

}
