package com.lcap.debugger;

import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.lcap.debugger.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        
        // 设置退出确认回调
        setupExitConfirmation(navController);
    }
    
    private void setupExitConfirmation(NavController navController) {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 检查当前是否在主页面（FirstFragment）
                if (navController.getCurrentDestination() != null && 
                    navController.getCurrentDestination().getId() == R.id.FirstFragment) {
                    // 在主页面，显示退出确认对话框
                    showExitConfirmationDialog();
                } else {
                    // 不在主页面，执行正常返回
                    setEnabled(false);
                    onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }
    
    private void showExitConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("退出应用")
                .setMessage("确定要关闭应用吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    finish(); // 关闭应用
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss(); // 取消，什么都不做
                })
                .setCancelable(true)
                .show();
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    */

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}