package com.example.skyeos;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.skyeos.ui.fragment.CaptureFragment;
import com.example.skyeos.ui.fragment.ProjectsFragment;
import com.example.skyeos.ui.fragment.ReviewFragment;
import com.example.skyeos.ui.fragment.SettingsFragment;
import com.example.skyeos.ui.fragment.TodayFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply the new LifeOS theme
        setTheme(R.style.Theme_LifeOS);
        setContentView(R.layout.activity_main);

        AppGraph.getInstance(this); // init graph eagerly

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_today) {
                showFragment(new TodayFragment());
            } else if (id == R.id.nav_capture) {
                showFragment(new CaptureFragment());
            } else if (id == R.id.nav_projects) {
                showFragment(new ProjectsFragment());
            } else if (id == R.id.nav_review) {
                showFragment(new ReviewFragment());
            } else if (id == R.id.nav_settings) {
                showFragment(new SettingsFragment());
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

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
