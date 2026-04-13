package dev.domin.punisher.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART_PATTERN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Duration parseDuration(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("perm") || normalized.equals("permanent") || normalized.equals("forever") || normalized.equals("0")) {
            return Duration.ZERO;
        }

        Matcher matcher = PART_PATTERN.matcher(normalized);
        long totalSeconds = 0L;
        int cursor = 0;

        while (matcher.find()) {
            if (matcher.start() != cursor) {
                return null;
            }

            long amount = Long.parseLong(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));

            totalSeconds += switch (unit) {
                case 's' -> amount;
                case 'm' -> amount * 60;
                case 'h' -> amount * 60 * 60;
                case 'd' -> amount * 60 * 60 * 24;
                case 'w' -> amount * 60 * 60 * 24 * 7;
                default -> 0;
            };

            cursor = matcher.end();
        }

        if (cursor != normalized.length()) {
            return null;
        }

        if (totalSeconds <= 0) {
            return null;
        }

        return Duration.ofSeconds(totalSeconds);
    }

    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "permanent";
        }

        long seconds = duration.toSeconds();
        long weeks = seconds / (7 * 24 * 60 * 60);
        seconds %= (7 * 24 * 60 * 60);
        long days = seconds / (24 * 60 * 60);
        seconds %= (24 * 60 * 60);
        long hours = seconds / (60 * 60);
        seconds %= (60 * 60);
        long minutes = seconds / 60;
        seconds %= 60;

        List<String> parts = new ArrayList<>();
        if (weeks > 0) {
            parts.add(weeks + "w");
        }
        if (days > 0) {
            parts.add(days + "d");
        }
        if (hours > 0) {
            parts.add(hours + "h");
        }
        if (minutes > 0) {
            parts.add(minutes + "m");
        }
        if (seconds > 0) {
            parts.add(seconds + "s");
        }

        if (parts.isEmpty()) {
            return "0s";
        }
        return String.join(" ", parts);
    }
}
