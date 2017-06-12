package com.example.shayne.myapplication;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/**
 * 点击返回键 将触发此类
 * 提供了布局文件，并为布局文件控件绑定监听器
 * @author Administrator
 *
 */
public class ExitActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exit_dialog);
    }


    public void exitButtonYes(View v) {
        this.finish();
        MainActivity.instance.finish();//结束MainActivity
    }

    public void exitButtonNo(View v) {
        this.finish();// 结束本类
    }
}
