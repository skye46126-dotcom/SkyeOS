package com.example.skyeos.data.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MigrationRegistry {
    private MigrationRegistry() {}

    public static List<MigrationSpec> all() {
        List<MigrationSpec> migrations = new ArrayList<>();
        migrations.add(new MigrationSpec(
                1,
                "init_schema",
                "db/migrations/V001__init.sql",
                "v001-init"
        ));
        return Collections.unmodifiableList(migrations);
    }
}

