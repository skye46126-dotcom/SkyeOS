package com.example.skyeos.domain.repository;

import com.example.skyeos.domain.model.ProjectOption;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.WindowOverview;

import java.util.List;

public interface LifeOsReadRepository {
    WindowOverview getOverview(String anchorDate, String windowType);

    List<RecentRecordItem> getRecentRecords(int limit);

    List<ProjectOption> getProjectOptions(boolean includeDone);
}
