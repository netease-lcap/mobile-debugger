package com.lcap.debugger;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.lcap.debugger.databinding.FragmentFirstBinding;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final String PREFS_NAME = "webview_history";
    private static final String HISTORY_KEY = "url_history";
    
    // 扫码结果回调
    private final ActivityResultLauncher<ScanOptions> scanLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String scannedText = result.getContents();
                
                        binding.urlInput.setText(scannedText);
                        loadWebPageFromInput();
                        Toast.makeText(getContext(), "扫码成功！正在加载网页...", Toast.LENGTH_SHORT).show();
                   
                }
            }
    );

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 配置WebView
        WebView webView = binding.webview;
        EditText urlInput = binding.urlInput;
        
        // 检查是否从历史记录页面传递了URL（通过SavedStateHandle）
        NavController navController = NavHostFragment.findNavController(this);
        navController.getCurrentBackStackEntry().getSavedStateHandle()
                .getLiveData("selected_url", "")
                .observe(getViewLifecycleOwner(), selectedUrl -> {
                    if (selectedUrl != null && !selectedUrl.isEmpty()) {
                        urlInput.setText(selectedUrl);
                        // 延迟一下确保WebView配置完成后再加载
                        urlInput.post(() -> loadWebPageFromInput());
                        // 清除已处理的数据
                        navController.getCurrentBackStackEntry().getSavedStateHandle().remove("selected_url");
                    }
                });
        
        // 检查是否从Bundle传递了URL（保持向后兼容）
        Bundle args = getArguments();
        if (args != null && args.containsKey("selected_url")) {
            String selectedUrl = args.getString("selected_url");
            if (selectedUrl != null && !selectedUrl.isEmpty()) {
                urlInput.setText(selectedUrl);
                // 延迟一下确保WebView配置完成后再加载
                urlInput.post(() -> loadWebPageFromInput());
            }
        }
        
        // 启用WebView调试（用于Chrome DevTools）
        WebView.setWebContentsDebuggingEnabled(true);
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // 设置WebViewClient以在应用内打开链接
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 注入JavaScript来支持HTML5 History API检测
                injectHistoryDetectionScript(view);
            }
        });

        // 创建加载网页的方法
        Runnable loadUrl = () -> {
            String url = urlInput.getText().toString().trim();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), "请输入网址", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 如果URL不包含协议，自动添加https://
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            
            // 保存到历史记录
            saveToHistory(url);
            
            // 重置WebView
            resetWebView();
            
            webView.loadUrl(url);
        };

        // 加载按钮点击事件
        binding.loadButton.setOnClickListener(v -> loadUrl.run());

        // 扫码按钮点击事件
        binding.scanButton.setOnClickListener(v -> startQRCodeScan());

        // 历史记录按钮点击事件
        binding.historyButton.setOnClickListener(v -> {
            NavHostFragment.findNavController(FirstFragment.this)
                    .navigate(R.id.action_FirstFragment_to_SecondFragment);
        });

        // 输入框回车键事件
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadUrl.run();
                return true;
            }
            return false;
        });

        // 确保WebView获得初始焦点，避免输入框自动聚焦
        webView.requestFocus();
        
        // 设置返回键劫持：如果WebView有历史记录则调用WebView返回
        setupBackPressedCallback(webView);
        
        // 不再自动加载，等待用户输入或扫码
    }
    
    private OnBackPressedCallback backPressedCallback;
    
    private void setupBackPressedCallback(WebView webView) {
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 首先检查WebView原生历史记录
                if (webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                
                // 检查HTML5 History API状态
                checkHtml5HistoryAndGoBack(webView);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
    }
    
    // 注入JavaScript来检测HTML5 History状态
    private void injectHistoryDetectionScript(WebView webView) {
        String script = 
            "window.androidHistoryLength = history.length;" +
            "window.androidCanGoBack = function() {" +
            "    return history.length > 1;" +
            "};" +
            "window.androidGoBack = function() {" +
            "    if (history.length > 1) {" +
            "        history.back();" +
            "        return true;" +
            "    }" +
            "    return false;" +
            "};" +
            // 监听popstate事件，当history.back()执行时触发
            "window.androidHistoryBackExecuted = false;" +
            "window.addEventListener('popstate', function() {" +
            "    window.androidHistoryBackExecuted = true;" +
            "});";
        
        webView.evaluateJavascript(script, null);
    }
    
    // 检查HTML5 History并执行返回操作
    private void checkHtml5HistoryAndGoBack(WebView webView) {
        webView.evaluateJavascript("window.androidCanGoBack ? window.androidCanGoBack() : false", 
            result -> {
                boolean canGoBack = "true".equals(result);
                if (canGoBack) {
                    // HTML5 History中有记录，执行JavaScript返回
                    webView.evaluateJavascript(
                        "window.androidHistoryBackExecuted = false; window.androidGoBack();", 
                        null
                    );
                    // 等待一小段时间确认返回操作是否成功
                    webView.postDelayed(() -> {
                        webView.evaluateJavascript("window.androidHistoryBackExecuted", successResult -> {
                            if (!"true".equals(successResult)) {
                                // 如果JavaScript返回失败，则执行正常的Fragment返回
                                requireActivity().runOnUiThread(() -> {
                                    backPressedCallback.setEnabled(false);
                                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                                    webView.postDelayed(() -> {
                                        if (backPressedCallback != null) {
                                            backPressedCallback.setEnabled(true);
                                        }
                                    }, 100);
                                });
                            }
                        });
                    }, 50);
                } else {
                    // 没有任何历史记录，暂时禁用当前回调并触发MainActivity的处理
                    requireActivity().runOnUiThread(() -> {
                        backPressedCallback.setEnabled(false);
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        // 延迟重新启用回调，避免立即重新触发
                        webView.postDelayed(() -> {
                            if (backPressedCallback != null) {
                                backPressedCallback.setEnabled(true);
                            }
                        }, 100);
                    });
                }
            });
    }

    // 从输入框加载网页（用于扫码后调用）
    private void loadWebPageFromInput() {
        String url = binding.urlInput.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(getContext(), "请输入网址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 如果URL不包含协议，自动添加https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        
        // 保存到历史记录
        saveToHistory(url);
        
        // 重置WebView
        resetWebView();
        
        binding.webview.loadUrl(url);
    }

    // 重置WebView状态
    private void resetWebView() {
        WebView webView = binding.webview;
        
        // 停止当前加载
        webView.stopLoading();
        
        // 清除历史记录
        webView.clearHistory();
        
        // 清除缓存
        webView.clearCache(true);
        
        // 清除表单数据
        webView.clearFormData();
        
        // 清除SSL错误
        webView.clearSslPreferences();
        
        // 移除所有Cookie
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.CookieManager.getInstance().flush();
        
        // 清除WebView的所有视图状态
        webView.clearView();
        
        // 重新配置WebViewClient（确保设置不会丢失）
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 注入JavaScript来支持HTML5 History API检测
                injectHistoryDetectionScript(view);
            }
        });
    }

    // 启动二维码扫描
    private void startQRCodeScan() {
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            // 请求相机权限
            ActivityCompat.requestPermissions(requireActivity(), 
                    new String[]{Manifest.permission.CAMERA}, 
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            // 已有权限，启动扫码
            launchQRCodeScanner();
        }
    }

    // 启动二维码扫描器
    private void launchQRCodeScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("将二维码对准扫描框");
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        options.setOrientationLocked(false);
        
        scanLauncher.launch(options);
    }


    // 权限请求结果处理
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，启动扫码
                launchQRCodeScanner();
            } else {
                // 权限被拒绝
                Toast.makeText(getContext(), "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 保存URL到历史记录
    private void saveToHistory(String url) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existingHistory = prefs.getString(HISTORY_KEY, "");
        
        // 如果URL已存在，先移除它（避免重复）
        String[] urls = existingHistory.split("\n");
        StringBuilder newHistory = new StringBuilder();
        
        // 添加新URL到最前面
        newHistory.append(url).append("\n");
        
        // 添加其他URL（除了重复的）
        int count = 0;
        for (String existingUrl : urls) {
            if (!existingUrl.trim().isEmpty() && !existingUrl.equals(url) && count < 49) {
                newHistory.append(existingUrl).append("\n");
                count++;
            }
        }
        
        // 保存更新后的历史记录（最多50条）
        prefs.edit().putString(HISTORY_KEY, newHistory.toString()).apply();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}