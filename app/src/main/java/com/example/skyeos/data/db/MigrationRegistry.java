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
        migrations.add(new MigrationSpec(
                2,
                "tag_scope_and_relations",
                "db/migrations/V002__tag_scope_and_relations.sql",
                "v002-tag-scope-relations"
        ));
        migrations.add(new MigrationSpec(
                3,
                "multi_user_foundation",
                "db/migrations/V003__multi_user_foundation.sql",
                "v003-multi-user-foundation"
        ));
        return Collections.unmodifiableList(migrations);
    }
}
