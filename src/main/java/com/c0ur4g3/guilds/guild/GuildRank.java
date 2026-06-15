package com.c0ur4g3.guilds.guild;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public class GuildRank {

    private final String key;
    private String displayName;
    private int priority;
    private final Set<GuildPermission> permissions;

    public GuildRank(String key, String displayName, int priority, Set<GuildPermission> permissions) {
        this.key = key;
        this.displayName = displayName;
        this.priority = priority;
        this.permissions = EnumSet.copyOf(permissions.isEmpty() ? EnumSet.of(GuildPermission.CHAT_ACCESS) : permissions);
    }

    public boolean hasPermission(GuildPermission permission) {
        return permissions.contains(permission);
    }

    public void addPermission(GuildPermission permission) {
        permissions.add(permission);
    }

    public void removePermission(GuildPermission permission) {
        permissions.remove(permission);
    }

    public String serializePermissions() {
        return permissions.stream()
                .map(GuildPermission::name)
                .collect(Collectors.joining(","));
    }

    public static Set<GuildPermission> deserializePermissions(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.of(GuildPermission.CHAT_ACCESS);
        }
        Set<GuildPermission> result = EnumSet.noneOf(GuildPermission.class);
        for (String part : raw.split(",")) {
            try {
                result.add(GuildPermission.valueOf(part.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result.isEmpty() ? EnumSet.of(GuildPermission.CHAT_ACCESS) : result;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Set<GuildPermission> getPermissions() { return permissions; }
}