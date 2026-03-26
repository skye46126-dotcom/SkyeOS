package com.example.skyeos.data.db;

public final class MigrationSpec {
    public final int version;
    public final String name;
    public final String assetPath;
    public final String checksum;

    public MigrationSpec(int version, String name, String assetPath, String checksum) {
        this.version = version;
        this.name = name;
        this.assetPath = assetPath;
        this.checksum = checksum;
    }
}

