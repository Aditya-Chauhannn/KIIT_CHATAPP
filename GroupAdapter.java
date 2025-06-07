package com.example.kiit_chatapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kiit_chatapp.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private List<Map<String, Object>> groupList;
    private OnGroupDeleteListener deleteListener;
    // Add maps for unread logic, but allow null for legacy use
    private Map<String, Long> lastReadMap;
    private Map<String, Long> latestMsgMap;

    public interface OnGroupDeleteListener {
        void onDeleteGroupClicked(int position);
    }

    // Backward-compatible constructor for legacy use (no unread dot)
    public GroupAdapter(List<Map<String, Object>> groupList, OnGroupDeleteListener listener) {
        this(groupList, listener, new HashMap<>(), new HashMap<>());
    }

    // Full constructor with unread dot support
    public GroupAdapter(
            List<Map<String, Object>> groupList,
            OnGroupDeleteListener listener,
            Map<String, Long> lastReadMap,
            Map<String, Long> latestMsgMap
    ) {
        this.groupList = groupList;
        this.deleteListener = listener;
        this.lastReadMap = lastReadMap != null ? lastReadMap : new HashMap<>();
        this.latestMsgMap = latestMsgMap != null ? latestMsgMap : new HashMap<>();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Map<String, Object> group = groupList.get(position);
        String groupId = group.get("groupId") != null ? group.get("groupId").toString() : null;
        String name = (String) group.get("groupName");
        holder.txtGroupName.setText(name != null ? name : "Unnamed Group");
        holder.groupTopicText.setText("Do Not Use Any Vulgar Words, Contact 22052184@kiit.ac.in for support.");

        // UNREAD DOT LOGIC
        holder.unreadDot.setVisibility(View.GONE); // Default: hidden
//        holder.unreadDot.setVisibility(View.VISIBLE);


        if (groupId != null && latestMsgMap != null && lastReadMap != null) {
            Long latestMsg = latestMsgMap.get(groupId);
            Long lastRead = lastReadMap.get(groupId);
            if (latestMsg != null && latestMsg > 0) {
                if (lastRead == null || lastRead < latestMsg) {
                    holder.unreadDot.setVisibility(View.VISIBLE);
                }
            }
        }

        holder.btnDeleteGroup.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteGroupClicked(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView txtGroupName;
        Button btnDeleteGroup, joinButton;
        TextView groupTopicText;
        View unreadDot;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            txtGroupName = itemView.findViewById(R.id.txtGroupName);
            btnDeleteGroup = itemView.findViewById(R.id.btnDeleteGroup);
            joinButton = itemView.findViewById(R.id.joinButton);
            groupTopicText = itemView.findViewById(R.id.groupTopicText);
            unreadDot = itemView.findViewById(R.id.unreadDot); // Make sure your XML has this view!
        }
    }
}