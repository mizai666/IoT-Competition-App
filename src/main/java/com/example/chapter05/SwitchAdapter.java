package com.example.chapter05;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SwitchAdapter extends RecyclerView.Adapter<SwitchAdapter.ViewHolder> {

    public interface Listener {
        void onSwitchToggle(int position, boolean checked);
        void onSwitchLongClick(int position);
    }

    private List<SwitchItem> switches;
    private boolean autoMode;
    private final Listener listener;

    public SwitchAdapter(List<SwitchItem> switches, boolean autoMode, Listener listener) {
        this.switches = switches;
        this.autoMode = autoMode;
        this.listener = listener;
    }

    public void updateData(List<SwitchItem> newSwitches) {
        this.switches = newSwitches;
        notifyDataSetChanged();
    }

    public void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_switch, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SwitchItem item = switches.get(position);
        holder.nameText.setText(item.getDisplayName());
        holder.keyText.setText(item.getJsonKey());
        holder.switchCompat.setChecked(item.isState());
        holder.switchCompat.setEnabled(!autoMode);

        holder.switchCompat.setOnCheckedChangeListener(null);
        holder.switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onSwitchToggle(pos, isChecked);
            }
        });

        holder.container.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onSwitchLongClick(pos);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return switches.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View container;
        TextView nameText;
        TextView keyText;
        SwitchCompat switchCompat;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.switch_left_container);
            nameText = itemView.findViewById(R.id.tv_switch_left_name);
            keyText = itemView.findViewById(R.id.tv_switch_left_key);
            switchCompat = itemView.findViewById(R.id.switch_left);
        }
    }
}
