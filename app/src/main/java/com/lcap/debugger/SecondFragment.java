package com.lcap.debugger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lcap.debugger.databinding.FragmentSecondBinding;

import java.util.ArrayList;
import java.util.List;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private static final String PREFS_NAME = "webview_history";
    private static final String HISTORY_KEY = "url_history";
    private HistoryAdapter historyAdapter;
    private List<String> historyList;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化RecyclerView
        setupRecyclerView();

        // 加载历史记录
        loadHistory();

        binding.buttonSecond.setOnClickListener(v ->
                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment)
        );
    }

    private void setupRecyclerView() {
        historyList = new ArrayList<>();
        historyAdapter = new HistoryAdapter(historyList, this::onHistoryItemClick);
        
        binding.historyRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.historyRecyclerView.setAdapter(historyAdapter);
    }

    private void onHistoryItemClick(String url) {
        // 使用Bundle传递选中的URL到FirstFragment
        Bundle bundle = new Bundle();
        bundle.putString("selected_url", url);
        
        // 获取NavController
        NavController navController = NavHostFragment.findNavController(SecondFragment.this);
        
        // 先设置结果数据到SavedStateHandle
        navController.getPreviousBackStackEntry().getSavedStateHandle().set("selected_url", url);
        
        // 然后返回到上一个页面（FirstFragment）
        navController.popBackStack();
    }

    private void loadHistory() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String historyString = prefs.getString(HISTORY_KEY, "");
        
        historyList.clear();
        
        if (historyString.isEmpty()) {
            showEmptyState();
            return;
        }
        
        String[] urls = historyString.split("\n");
        
        for (String url : urls) {
            if (!url.trim().isEmpty()) {
                historyList.add(url.trim());
            }
        }
        
        if (historyList.isEmpty()) {
            showEmptyState();
        } else {
            showHistoryList();
        }
    }

    private void showEmptyState() {
        binding.historyRecyclerView.setVisibility(View.GONE);
        binding.textviewSecond.setVisibility(View.VISIBLE);
        binding.textviewSecond.setText("暂无历史记录\n\n开始浏览网页后，历史记录会显示在这里");
    }

    private void showHistoryList() {
        binding.historyRecyclerView.setVisibility(View.VISIBLE);
        binding.textviewSecond.setVisibility(View.GONE);
        historyAdapter.notifyDataSetChanged();
    }

    // 历史记录适配器
    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<String> historyList;
        private final OnItemClickListener onItemClickListener;

        public interface OnItemClickListener {
            void onItemClick(String url);
        }

        public HistoryAdapter(List<String> historyList, OnItemClickListener onItemClickListener) {
            this.historyList = historyList;
            this.onItemClickListener = onItemClickListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String url = historyList.get(position);
            holder.urlNumber.setText(String.valueOf(position + 1));
            holder.urlText.setText(url);
            
            holder.itemView.setOnClickListener(v -> onItemClickListener.onItemClick(url));
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView urlNumber;
            TextView urlText;

            ViewHolder(View view) {
                super(view);
                urlNumber = view.findViewById(R.id.url_number);
                urlText = view.findViewById(R.id.url_text);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}