package com.example.chapter05;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThresholdAdapter extends RecyclerView.Adapter<ThresholdAdapter.ViewHolder> {
    private Context context;
    private List<ThresholdItem> thresholdList;
    private Map<String, Float> currentValues;
    private Map<Integer, Boolean> triggeredState;
    private OnThresholdLongClickListener longClickListener;
    private OnAlarmHistoryClickListener alarmHistoryClickListener;
    private Vibrator vibrator;

    public interface OnThresholdLongClickListener {
        void onLongClick(int position);
    }

    public interface OnAlarmHistoryClickListener {
        void onAlarmHistoryClick(int position);
    }

    public ThresholdAdapter(Context context, List<ThresholdItem> list,
                            OnThresholdLongClickListener longListener,
                            OnAlarmHistoryClickListener alarmListener) {
        this.context = context;
        this.thresholdList = list;
        this.longClickListener = longListener;
        this.alarmHistoryClickListener = alarmListener;
        this.currentValues = new HashMap<>();
        this.triggeredState = new HashMap<>();
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void updateCurrentValue(String sensorName, float value) {
        currentValues.put(sensorName, value);
        notifyDataSetChanged();
    }

    public void updateData(List<ThresholdItem> newList) {
        this.thresholdList = newList;
        triggeredState.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_threshold, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ThresholdItem item = thresholdList.get(position);
        String sensorName = item.getSensorName();   // 底层键名（用于匹配数据）
        String displayName = item.getDisplayName(); // 显示名称
        float min = item.getMinValue();
        float max = item.getMaxValue();
        Float current = currentValues.get(sensorName); // 注意：这里仍用 sensorName 作为 key

        holder.tvSensorName.setText(displayName);  // 显示用户自定义名称
        holder.tvRange.setText(String.format("阈值范围: %.2f ~ %.2f", min, max));
        holder.tvMessage.setText(item.getMessage() != null ? item.getMessage() : "无消息");

        boolean outOfRange = false;
        String currentStr = "暂无数据";
        if (current != null) {
            currentStr = String.format("当前值: %.2f", current);
            outOfRange = (current < min || current > max);
        }
        holder.tvCurrentValue.setText(currentStr);

        if (outOfRange) {
            holder.itemView.setBackgroundResource(R.drawable.bg_threshold_warning);
            Boolean already = triggeredState.get(position);
            if (already == null || !already) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                }
                triggeredState.put(position, true);
            }
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_threshold_normal);
            triggeredState.put(position, false);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(position);
            }
            return true;
        });

        holder.btnAlarmHistory.setOnClickListener(v -> {
            if (alarmHistoryClickListener != null) {
                alarmHistoryClickListener.onAlarmHistoryClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return thresholdList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSensorName, tvRange, tvCurrentValue, tvMessage;
        Button btnAlarmHistory;
        ViewHolder(View itemView) {
            super(itemView);
            tvSensorName = itemView.findViewById(R.id.tv_sensor_name);
            tvRange = itemView.findViewById(R.id.tv_range);
            tvCurrentValue = itemView.findViewById(R.id.tv_current_value);
            tvMessage = itemView.findViewById(R.id.tv_message);
            btnAlarmHistory = itemView.findViewById(R.id.btn_alarm_history);
        }
    }
}