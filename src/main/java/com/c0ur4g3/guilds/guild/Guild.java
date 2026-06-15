package com.c0ur4g3.guilds.guild;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Guild {

    private final UUID id;
    private String name;
    private String tag;
    private UUID owner;
    private double balance;
    private boolean friendlyFire;
    private final Map<UUID, GuildMember> members = new ConcurrentHashMap<>();
    private final Map<String, GuildRank> ranks = new ConcurrentHashMap<>();
    private final Set<UUID> allies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> enemies = ConcurrentHashMap.newKeySet();
    public Guild(UUID id, String name, String tag, UUID owner, double balance, boolean friendlyFire) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.owner = owner;
        this.balance = balance;
        this.friendlyFire = friendlyFire;
    }

    public synchronized void setBalance(double balance) {
        this.balance = balance;
    }

    public synchronized void deposit(double amount) {
        this.balance += amount;
    }

    public synchronized boolean withdraw(double amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    public void addMember(GuildMember member) {
        members.put(member.getPlayerUuid(), member);
    }

    public void removeMember(UUID playerUuid) {
        members.remove(playerUuid);
    }

    public Optional<GuildMember> getMember(UUID playerUuid) {
        return Optional.ofNullable(members.get(playerUuid));
    }

    public boolean isMember(UUID playerUuid) {
        return members.containsKey(playerUuid);
    }

    public Collection<GuildMember> getAllMembers() {
        return members.values();
    }

    public int getMemberCount() {
        return members.size();
    }

    public void addRank(GuildRank rank) {
        ranks.put(rank.getKey(), rank);
    }

    public Optional<GuildRank> getRank(String key) {
        return Optional.ofNullable(ranks.get(key));
    }

    public Optional<GuildRank> getRankForMember(UUID playerUuid) {
        return getMember(playerUuid)
                .map(GuildMember::getRankKey)
                .flatMap(this::getRank);
    }

    public boolean memberHasPermission(UUID playerUuid, GuildPermission permission) {
        return getRankForMember(playerUuid)
                .map(rank -> rank.hasPermission(permission))
                .orElse(false);
    }

    public Collection<GuildRank> getAllRanks() {
        return ranks.values();
    }

    public Map<String, GuildRank> getRanks() {
        return ranks;
    }

    public int getMemberPriority(UUID playerUuid) {
        return getRankForMember(playerUuid).map(GuildRank::getPriority).orElse(0);
    }

    public Optional<GuildRank> getHighestRank() {
        return getAllRanks().stream()
                .max(Comparator.comparingInt(GuildRank::getPriority));
    }

    public Optional<GuildRank> getAdjacentRank(String currentRankKey, int direction) {
        List<GuildRank> sorted = getAllRanks().stream()
                .sorted(Comparator.comparingInt(GuildRank::getPriority))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(currentRankKey)) {
                int next = i + direction;
                if (next >= 0 && next < sorted.size()) {
                    return Optional.of(sorted.get(next));
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public boolean canManageMember(UUID actorUuid, UUID targetUuid) {
        if (actorUuid.equals(targetUuid)) {
            return false;
        }
        return getMemberPriority(actorUuid) > getMemberPriority(targetUuid);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public double getBalance() { return balance; }
    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }
    public Set<UUID> getAllies() { return allies; }
    public Set<UUID> getEnemies() { return enemies; }
}