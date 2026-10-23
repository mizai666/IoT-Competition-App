package com.example.chapter05;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局报警卡片渲染工具。在容器顶部插入报警卡片，5 秒自动消失，可手动关闭。
 */
public class AlarmCardHelper {

    public static void show(LinearLayout container, Context context, AlarmCenter.AlarmItem alarm) {
        if (container == null) return;

        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(context, 8));
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(0xFFFEE2E2);
        card.setCardElevation(dp(context, 4));
        card.setRadius(dp(context, 12));
        card.setContentPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textParams);

        TextView timeTv = new TextView(context);
        timeTv.setTextSize(12);
        timeTv.setTextColor(0xFFB91C1C);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        timeTv.setText(sdf.format(new Date(alarm.timestamp)));

        TextView msgTv = new TextView(context);
        msgTv.setTextSize(14);
        msgTv.setTextColor(0xFF7F1D1D);
        msgTv.setText(alarm.message);

        textCol.addView(timeTv);
        textCol.addView(msgTv);

        Button closeBtn = new Button(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(0xFF7F1D1D);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        closeBtn.setOnClickListener(v -> container.removeView(card));

        row.addView(textCol);
        row.addView(closeBtn);

        card.addView(row);
        container.addView(card, 0);

        // 5 秒后自动移除
        new Handler(Looper.getMainLooper()).postDelayed(() -> container.removeView(card), 5000);
    }

    private static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
