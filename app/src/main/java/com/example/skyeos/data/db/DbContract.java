package com.example.skyeos.data.db;

public final class DbContract {
    private DbContract() {}

    public static final String DATABASE_NAME = "lifeos.db";
    public static final int DATABASE_VERSION = 8;

    public static final class MigrationTable {
        public static final String NAME = "migration";
        public static final String COL_VERSION = "version";
        public static final String COL_NAME = "name";
        public static final String COL_CHECKSUM = "checksum";
        public static final String COL_APPLIED_AT = "applied_at";

        private MigrationTable() {}
    }
}
