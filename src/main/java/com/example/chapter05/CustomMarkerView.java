package com.example.chapter05;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;

public class CustomMarkerView extends MarkerView {
    private final TextView tvContent;

    public CustomMarkerView(Context context) {
        super(context, R.layout.custom_marker_view);
        tvContent = findViewById(R.id.tv_marker_content);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        // 内容由外部动态设置，这里不做处理
        super.refreshContent(e, highlight);
    }

    public void setContent(String text) {
        tvContent.setText(text);
    }
}