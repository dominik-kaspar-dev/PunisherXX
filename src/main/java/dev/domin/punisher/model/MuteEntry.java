package dev.domin.punisher.model;

public record MuteEntry(
        String playerName,
        String reason,
        String actor,
        long createdAt,
        long expiresAt
) {
    public boolean isPermanent() {
        return expiresAt <= 0;
    }

    public boolean isExpired(long now) {
        return !isPermanent() && expiresAt <= now;
    }
}
