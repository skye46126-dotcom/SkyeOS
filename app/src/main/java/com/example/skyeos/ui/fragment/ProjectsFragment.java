package com.example.skyeos.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.domain.model.ProjectOverview;
import com.google.android.material.tabs.TabLayout;

import java.util.List;
import java.util.Locale;

public class ProjectsFragment extends Fragment {

    private AppGraph graph;
    private TabLayout tabLayout;
    private RecyclerView rvProjects;
    private TextView tvProjectsSummary;
    private ProjectAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_projects, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        graph = AppGraph.getInstance(requireContext());

        tabLayout = view.findViewById(R.id.tab_layout_projects);
        rvProjects = view.findViewById(R.id.rv_projects);
        tvProjectsSummary = view.findViewById(R.id.tv_projects_summary);

        rvProjects.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ProjectAdapter(projectId -> {
            if (!isAdded() || getParentFragmentManager().isStateSaved()) {
                return;
            }
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, ProjectDetailFragment.newInstance(projectId))
                    .addToBackStack("project_detail")
                    .commit();
        });
        rvProjects.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadProjects(tab.getPosition() == 0 ? "active" : "done");
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        loadProjects("active");
        refreshSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProjects(tabLayout.getSelectedTabPosition() == 0 ? "active" : "done");
        refreshSummary();
    }

    private void loadProjects(String statusType) {
        List<ProjectOverview> projects = graph.useCases.projectUseCases.getProjects(statusType);
        adapter.submitList(projects);

        View placeholder = getView().findViewById(R.id.tv_projects_placeholder);
        if (placeholder != null) {
            placeholder.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
            rvProjects.setVisibility(projects.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void refreshSummary() {
        if (tvProjectsSummary == null) {
            return;
        }
        List<ProjectOverview> active = graph.useCases.projectUseCases.getProjects("active");
        List<ProjectOverview> done = graph.useCases.projectUseCases.getProjects("done");
        tvProjectsSummary.setText(String.format(Locale.US, "%d active · %d done", active.size(), done.size()));
    }
}
