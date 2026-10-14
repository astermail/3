package com.gaspoweredcake.client33.mixin.baritone;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Baritone's perpetual cache workers must not keep the client alive after Quit. */
@Pseudo
@Mixin(targets = "baritone.Baritone", remap = false)
public abstract class BaritoneExecutorMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "NEW", target = "java/util/concurrent/ThreadPoolExecutor"), remap = false)
    private static ThreadPoolExecutor client33$manageWorkerLifetime(ThreadPoolExecutor executor) {
        var factory = executor.getThreadFactory();
        executor.setThreadFactory(task -> {
            Thread thread = factory.newThread(task);
            thread.setDaemon(true);
            return thread;
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Let pending cache saves finish without waiting forever for the packing loops.
            executor.shutdown();
            try { executor.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }, "Client33-Baritone-Shutdown"));
        return executor;
    }
}
