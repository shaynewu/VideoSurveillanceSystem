package com.example.shayne.myapplication;

import android.app.Activity;
import android.os.Bundle;
/**
 * 设置窗口
 * 只用到了布局文件main_settings
 * @author Administrator
 *
 */
public class SettingActivity extends Activity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_settings);/*提供点"设置"时显示的画面*/
    }
}
