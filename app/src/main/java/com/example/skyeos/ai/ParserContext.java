package com.example.skyeos.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides contextual information to the parser engines, such as known
 * projects,
 * categories, and hints extracted by previous parser passes.
 */
public final class ParserContext {
    public final List<String> categories = new ArrayList<>();
    public final List<String> projectNames = new ArrayList<>();
    public final List<String> tagNames = new ArrayList<>();
    public final List<ParseDraftItem> ruleHints = new ArrayList<>();

    public ParserContext() {
    }

    public ParserContext(List<String> categories, List<String> projectNames, List<String> tagNames) {
        if (categories != null)
            this.categories.addAll(categories);
        if (projectNames != null)
            this.projectNames.addAll(projectNames);
        if (tagNames != null)
            this.tagNames.addAll(tagNames);
    }

    public void addRuleHint(ParseDraftItem item) {
        if (item != null) {
            this.ruleHints.add(item);
        }
    }
}
