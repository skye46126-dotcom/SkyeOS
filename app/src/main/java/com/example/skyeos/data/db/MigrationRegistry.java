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
        migrations.add(new MigrationSpec(
                4,
                "expense_structure",
                "db/migrations/V004__expense_structure.sql",
                "v004-expense-structure"
        ));
        migrations.add(new MigrationSpec(
                5,
                "ai_assist_ratio",
                "db/migrations/V005__ai_assist_ratio.sql",
                "v005-ai-assist-ratio"
        ));
        migrations.add(new MigrationSpec(
                6,
                "efficiency_scores",
                "db/migrations/V006__efficiency_scores.sql",
                "v006-efficiency-scores"
        ));
        migrations.add(new MigrationSpec(
                7,
                "learning_timeline_fields",
                "db/migrations/V007__learning_timeline_fields.sql",
                "v007-learning-timeline-fields"
        ));
        migrations.add(new MigrationSpec(
                8,
                "expense_project_relation",
                "db/migrations/V008__expense_project_relation.sql",
                "v008-expense-project-relation"
        ));
        return Collections.unmodifiableList(migrations);
    }
}
