package com.example.skyeos;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.skyeos.ui.fragment.CaptureFragment;
import com.example.skyeos.ui.fragment.CostManagementFragment;
import com.example.skyeos.ui.fragment.DayDetailFragment;
import com.example.skyeos.ui.fragment.LedgerManagementFragment;
import com.example.skyeos.ui.fragment.ManagementFragment;
import com.example.skyeos.ui.fragment.AiChatFragment;
import com.example.skyeos.ui.fragment.ProjectsFragment;
import com.example.skyeos.ui.fragment.ReviewFragment;
import com.example.skyeos.ui.fragment.SettingsFragment;
import com.example.skyeos.ui.fragment.TimeManagementFragment;
import com.example.skyeos.ui.fragment.TodayFragment;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private String pendingCaptureType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply the new LifeOS theme
        setTheme(R.style.Theme_LifeOS);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_today) {
                showPrimaryFragment(new TodayFragment());
            } else if (id == R.id.nav_capture) {
                if (pendingCaptureType == null) {
                    showPrimaryFragment(new CaptureFragment());
                } else {
                    showPrimaryFragment(CaptureFragment.newManualWithType(pendingCaptureType));
                    pendingCaptureType = null;
                }
            } else if (id == R.id.nav_management) {
                showPrimaryFragment(new ManagementFragment());
            } else if (id == R.id.nav_review) {
                showPrimaryFragment(new ReviewFragment());
            }
            return true;
        });

        // Start on Today tab
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_today);
        }
    }

    public void navigateTo(int navItemId) {
        bottomNav.setSelectedItemId(navItemId);
    }

    public void openSettings() {
        openUtilityFragment(new SettingsFragment());
    }

    public void openCostManagement() {
        openUtilityFragment(new CostManagementFragment());
    }

    public void openProjects() {
        openUtilityFragment(new ProjectsFragment());
    }

    public void openTimeManagement() {
        openUtilityFragment(new TimeManagementFragment());
    }

    public void openCaptureType(String type) {
        pendingCaptureType = type;
        bottomNav.setSelectedItemId(R.id.nav_capture);
    }

    public void openLedgerManagement(String type) {
        openUtilityFragment(LedgerManagementFragment.newInstance(type));
    }

    public void openDayDetail(String anchorDate) {
        openUtilityFragment(DayDetailFragment.newInstance(anchorDate));
    }

    public void openAiChat() {
        openUtilityFragment(new AiChatFragment());
    }

    private void showPrimaryFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void openUtilityFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(fragment.getClass().getSimpleName())
                .commit();
    }
}
