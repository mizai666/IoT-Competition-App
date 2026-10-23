package com.example.chapter05;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ViewHolder> {

    public interface Listener {
        void onThemeClick(int position);
        void onThemeLongClick(int position);
    }

    private List<ThemeItem> themes;
    private final Listener listener;

    public ThemeAdapter(List<ThemeItem> themes, Listener listener) {
        this.themes = themes;
        this.listener = listener;
    }

    public void updateData(List<ThemeItem> newThemes) {
        this.themes = newThemes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ThemeItem item = themes.get(position);
        holder.nameText.setText(item.getDisplayName());
        holder.codeText.setText(item.getThemeCode());
        holder.activeText.setVisibility(item.isActive() ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) listener.onThemeClick(pos);
        });
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) listener.onThemeLongClick(pos);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return themes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView codeText;
        TextView activeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.tv_theme_name);
            codeText = itemView.findViewById(R.id.tv_theme_code);
            activeText = itemView.findViewById(R.id.tv_theme_active);
        }
    }
}
