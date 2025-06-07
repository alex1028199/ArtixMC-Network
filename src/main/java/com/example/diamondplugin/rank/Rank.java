package com.example.diamondplugin.rank;

import java.util.List;
import java.util.Objects;

public class Rank {
    private String rankName;
    private String prefix;
    private List<String> permissions;

    public Rank(String rankName, String prefix, List<String> permissions) {
        this.rankName = rankName;
        this.prefix = prefix;
        this.permissions = permissions;
    }

    public String getRankName() {
        return rankName;
    }

    public void setRankName(String rankName) {
        this.rankName = rankName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rank rank = (Rank) o;
        return Objects.equals(rankName.toLowerCase(), rank.rankName.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(rankName.toLowerCase());
    }
}
