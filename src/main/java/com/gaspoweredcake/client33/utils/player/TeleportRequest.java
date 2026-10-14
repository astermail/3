package com.gaspoweredcake.client33.utils.player;

/** Validates one server command; teleport destinations are chosen by the server. */
public final class TeleportRequest {
    private TeleportRequest() {}
    public static String command(String configured) {
        String command = configured.strip();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank() || command.startsWith("/") || command.length() > 256
            || command.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Enter one server command, for example /rtp or /wild.");
        }
        return command;
    }
}
