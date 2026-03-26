package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import com.example.skyeos.domain.usecase.LifeOsUseCases;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.skyeos.MainActivity;
import com.example.skyeos.R;

@AndroidEntryPoint
public class ManagementFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;

    @Inject
    LifeOsUseCases useCases;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.card_mgmt_projects).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openProjects();
            }
        });
        view.findViewById(R.id.card_mgmt_income).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openLedgerManagement("income");
            }
        });
        view.findViewById(R.id.card_mgmt_expense).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openLedgerManagement("expense");
            }
        });
        view.findViewById(R.id.card_mgmt_time).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openTimeManagement();
            }
        });
        view.findViewById(R.id.card_mgmt_cost).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openCostManagement();
            }
        });
        view.findViewById(R.id.card_mgmt_settings).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSettings();
            }
        });
    }
}
