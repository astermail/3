package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.renderer.MeshCapacity;
import com.gaspoweredcake.client33.utils.render.ScreenProjection;
import org.joml.Vector3d;

public final class RenderRegression {
    public static void run() throws Exception {
        check(MeshCapacity.requiredBytes(1000, 20000, 16) == 336000, "Size uses the whole requested batch");
        check(MeshCapacity.grow(1024, 1024, 1024) == 1024, "Exact-fit buffers do not reallocate");
        check(MeshCapacity.grow(1024, 2049, 1024) >= 2049, "Growth satisfies requests larger than doubling");
        rejects(() -> MeshCapacity.requiredBytes(Integer.MAX_VALUE, 1, 16));
        rejects(() -> MeshCapacity.requiredBytes(0, -1, 4));
        check(MeshCapacity.grow(0, MeshCapacity.requiredBytes(0, 12000, 16), 16384) >= 192000,
            "First allocation satisfies a batch larger than the default");
        check(MeshCapacity.grow(0, MeshCapacity.requiredBytes(0, 18000, 4), 8192) >= 72000,
            "First index allocation satisfies a large batch");
        rejects(() -> MeshCapacity.grow(0, Integer.MAX_VALUE, 1024));

        var point = new Vector3d();
        check(ScreenProjection.project(0, 0, 0, 1, 1920, 1080, false, point) && point.equals(new Vector3d(960, 540, 0.5)), "Clip center maps to physical screen center");
        check(ScreenProjection.project(-1, 1, 0, 1, 1920, 1080, false, point) && point.x == 0 && point.y == 0, "Top-left maps correctly");
        check(!ScreenProjection.project(0, 0, 0, 0, 1920, 1080, false, point), "Camera plane never creates infinite labels");
        check(!ScreenProjection.project(0, 0, 0, 0.000001, 1920, 1080, false, point), "Unstable perspective division is rejected");
        check(!ScreenProjection.project(Double.NaN, 0, 0, 1, 1920, 1080, false, point), "NaN cannot escape into mesh coordinates");
        check(!ScreenProjection.project(0, 0, 0, -1, 1920, 1080, false, point), "Normal labels exclude points behind the camera");
        check(ScreenProjection.project(0, 0, 0, -1, 1920, 1080, true, point) && point.z < 0, "Directional arrows preserve behind-camera sign");
        System.out.println("Mesh capacity and overlay projection regressions passed.");
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("Invalid mesh size was accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
