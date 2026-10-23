package com.example.chapter05;

import android.content.Context;
import android.util.Log;

public class SpecialProjectManager {
    private static final String TAG = "SpecialProjectMgr";
    private static SpecialProjectManager instance;
    private Context appContext;

    private SpecialProjectManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized SpecialProjectManager getInstance(Context context) {
        if (instance == null) {
            instance = new SpecialProjectManager(context);
        }
        return instance;
    }

    public boolean isSpecialProject(int projectId) {
        ProjectDataManager dataManager = ProjectDataManager.getInstance(appContext);
        Project project = dataManager.getProject(projectId);
        if (project != null) {
            Log.d(TAG, "项目 " + projectId + " 是否为特殊项目: " + project.isSpecial());
            return project.isSpecial();
        }
        return false;
    }

    public boolean shouldHideChart(int projectId) {
        return isSpecialProject(projectId);
    }
}