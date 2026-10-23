package com.example.chapter05;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    public interface OnScheduleLongClickListener {
        void onLongClick(int position, View view);
    }

    private List<ScheduleItem> schedules;
    private final OnScheduleLongClickListener longClickListener;
    private final ScheduleManager scheduleManager;
    private final int projectId;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            notifyDataSetChanged();
            refreshHandler.postDelayed(this, 1000);
        }
    };

    public ScheduleAdapter(List<ScheduleItem> schedules, OnScheduleLongClickListener longClickListener,
                           ScheduleManager scheduleManager, int projectId) {
        this.schedules = schedules;
        this.longClickListener = longClickListener;
        this.scheduleManager = scheduleManager;
        this.projectId = projectId;
    }

    public void start() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, 1000);
    }

    public void stop() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    public void updateData(List<ScheduleItem> newSchedules) {
        this.schedules = newSchedules;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScheduleItem item = schedules.get(position);
        holder.nameText.setText(item.getDisplayName());
        String targetText = item.getTargetState() == 1 ? "定时开启" : "定时关闭";
        holder.infoText.setText(String.format("%s · 倒计时 %d 秒 · 剩余 %d 秒 · %s",
                targetText, item.getScheduleSeconds(), item.getRemainingSeconds(), item.getPublishTopic()));

        holder.activeSwitch.setOnCheckedChangeListener(null);
        holder.activeSwitch.setChecked(item.isEnabled());
        holder.activeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                scheduleManager.setScheduleEnabled(projectId, pos, isChecked);
            }
        });

        holder.container.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    longClickListener.onLongClick(pos, holder.container);
                }
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View container;
        TextView nameText;
        TextView infoText;
        SwitchCompat activeSwitch;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.schedule_container);
            nameText = itemView.findViewById(R.id.tv_schedule_name);
            infoText = itemView.findViewById(R.id.tv_schedule_info);
            activeSwitch = itemView.findViewById(R.id.switch_schedule_active);
        }
    }
}
