package com.example.chapter05;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SensorDataAdapter extends RecyclerView.Adapter<SensorDataAdapter.ViewHolder> {
    private Context context;
    private int projectId;
    private List<Map.Entry<String, String>> sensorList = new ArrayList<>();
    private OnAliasChangedListener aliasListener;

    public interface OnAliasChangedListener {
        void onAliasChanged();
    }

    public SensorDataAdapter(Context context, int projectId) {
        this.context = context;
        this.projectId = projectId;
    }

    public void setOnAliasChangedListener(OnAliasChangedListener listener) {
        this.aliasListener = listener;
    }

    public void updateData(Map<String, String> sensors) {
        sensorList.clear();
        if (sensors != null) {
            sensorList.addAll(sensors.entrySet());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map.Entry<String, String> entry = sensorList.get(position);
        String originalKey = entry.getKey();
        String value = entry.getValue();

        // 获取用户自定义显示名称，如果没有则使用原始 key
        String alias = SensorAliasHelper.resolve(context, projectId, originalKey);
        holder.text1.setText(alias);
        holder.text2.setText(value);

        // 长按编辑显示名称
        holder.itemView.setOnLongClickListener(v -> {
            showEditAliasDialog(originalKey, holder.text1.getText().toString());
            return true;
        });
    }

    private void showEditAliasDialog(String originalKey, String currentAlias) {
        EditText input = new EditText(context);
        input.setText(currentAlias);
        new AlertDialog.Builder(context)
                .setTitle("修改传感器显示名称")
                .setMessage("原始键名: " + originalKey)
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newAlias = input.getText().toString().trim();
                    if (newAlias.isEmpty()) {
                        newAlias = originalKey;
                    }
                    SensorAliasHelper.save(context, projectId, originalKey, newAlias);
                    // 刷新列表
                    notifyDataSetChanged();
                    if (aliasListener != null) aliasListener.onAliasChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return sensorList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        ViewHolder(View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
        }
    }
}