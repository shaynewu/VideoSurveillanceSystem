package com.example.shayne.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;

import android.view.KeyEvent;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.example.shayne.myapplication.io.MjpegInputStream;
import com.example.shayne.myapplication.tools.Generic;
import com.example.shayne.myapplication.view.MjpegView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * 登录成功后，将执行这里，此类继承了Activity实现了OnCheckedChangeListener监听器
 * OnCheckedChangeListener是当在RadioGroup的RadioButton被选中或改变时，一个回调接口被触发后执行
 * 主要有1个RadioGroup和5个RadioButton，实现了5个控件的跳转，但没都实现具体的操作，只是调到某个类去执行
 */
public class MainActivity extends Activity implements OnCheckedChangeListener {

    public static MainActivity instance = null;
    private MjpegInputStream mis = null;
    private MjpegView mjpegView = null;
    private RadioGroup mainTab = null;
    private File sdCardFile = null;
    private String picturePath = "";
    /**
     * 当该Activit第一次创建后，该方法即被触发
     * 1.设置布局
     * 2.初始化视频输入流
     * 3.根据在R.java的id找到控件
     * 4.为RadioGroup设置监听器，当RadioButton被按下或改变时触发下面的onCheckedChanged方法
     * 5.检查SD卡，初始化mjpegview视图，这样就可看到监控画面了
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);/*构造RadioGroup的5的RadioButton*/

        instance = this;
        mis = MjpegInputStream.getInstance();
        mjpegView = (MjpegView) findViewById(R.id.mjpegview);
        mainTab = (RadioGroup) findViewById(R.id.main_tab);
        mainTab.setOnCheckedChangeListener(this);

        checkSdcard();
        initMjpegView();
    }
    /**
     * 功能:获得SD路径
     * 如果有SD卡，则创建存放图片的picturePath目录
     */
    private void checkSdcard() {
        sdCardFile = Generic.getSdCardFile();
        if (sdCardFile == null)
            Generic.showMsg(this, "请插入SD卡", true);
        else {
            picturePath = sdCardFile.getAbsolutePath() + "/mjpeg/";
            File f = new File(picturePath);
            if (!(f.exists() && f.isDirectory()))
                f.mkdir();
        }
    }
    /**
     * 调用com/mjpeg/view的mjpegView.java类中mjpegView的众多方法来初始化自定义控件com.mjpeg.view.MjpegView
     * MjpegView类是重头戏
     */
    private void initMjpegView() {
        if (mis != null) {
            mjpegView.setSource(mis);// 设置数据来源
            mjpegView.setDisplayMode(mjpegView.getDisplayMode());/*设置mjpegview的显示模式*/
            /**
             * setFps和getFps方法是为了在屏幕的右上角动态显示当前的帧率
             * 如果我们只需观看画面，下面这句完全可以省去
             */
            mjpegView.setFps(mjpegView.getFps());
            /**
             * 调用mjpegView中的线程的run方法，开始显示画面
             */
            mjpegView.startPlay();
        }
    }

    @Override
    /**
     * 当本Activity被finish时，该方法被激发
     * 先调用mjpegview的stopplay方法，然后调用父类的onDestroy方法
     */
    protected void onDestroy() {
        if (mjpegView != null)
            mjpegView.stopPlay();
        super.onDestroy();
    }

    @Override
    /**
     * 当RadioGroup的成员改变时，该方法被调用
     * @parm group ：RadioButton所在的组
     * @parm checkedId：可以根据这个值来判断是哪个Button
     * 1.先不Checked RadioButton控件
     * 2.根据RadioGroup找到里面的Button成员的id
     * 3.根据ID获得RadioBotton控件
     * 4.根据点击不同的RadioBotton，执行相应的操作
     */
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        int radioButtonId = group.getCheckedRadioButtonId();
        // 根据ID获取RadioButton的实例
        RadioButton rb = ((RadioButton) this.findViewById(radioButtonId));
        rb.setChecked(false);

        switch (checkedId) {
            case R.id.radiobtn0:
                shotSnap(rb);
                break;
            case R.id.radiobtn1:
                /**
                 * 这里没实现录像功能，只弹出一个"录像"Toast
                 */
                Toast.makeText(this, "录像", Toast.LENGTH_SHORT).show();
                break;
            case R.id.radiobtn2:
                scanPic(); /*最复杂的浏览*/
                break;
            case R.id.radiobtn3:
                setFullScreen(rb);
                break;
            case R.id.radiobtn4:
                /**
                 * 跳转到settingActivi.java
                 */
                startActivity(new Intent(this, SettingActivity.class));
                break;
        }

    }

    @Override
    /**
     * 回调上一个Activity的结果处理函数
     * 当Activity调用resume()之前,正在重启Activity时调用该方法
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 显示模式设置
     * 1.获得当前显示模式
     * 2.如果当前是全屏显示，点击"set"按钮，Button的text将为"标准",点击将切换到标准模式
     * 3.如果当前是标准显示，点同一按钮，按钮变为"全屏",并切换到全屏显示模式
     * @param rb
     */
    private void setFullScreen(RadioButton rb) {
        int mode = mjpegView.getDisplayMode();

        if (mode == MjpegView.FULLSCREEN_MODE) {
            /**
             * 可以在xml文件设置RadioButton的text，也可以调用控件的的setText方法设置其text
             */
            rb.setText(R.string.fullscreen);
            mainTab.setBackgroundResource(R.drawable.maintab_toolbar_bg);/*黑条背景*/
            mjpegView.setDisplayMode(MjpegView.KEEP_SCALE_MODE);/*标准*/
        } else {
            rb.setText(R.string.standard);/*"标准"*/
            mainTab.setBackgroundColor(Color.TRANSPARENT);/*透明背景*/
            mjpegView.setDisplayMode(MjpegView.FULLSCREEN_MODE);/*全屏*/
        }
    }

    /**
     * 功能:拍照
     * @parm RadioButton rb
     * 1.先disable RadioButton,再使能它
     * 2.如果有SD卡，先在picturePath新建以当前系统时间为前缀的图片文件
     * 3.调用mjpegview的getbitmap方法获得位图
     * 4.位图 不为空，根据图片文件获得缓冲输出流
     * 5.调用位图的压缩方法将图片压缩为JPEG格式，刷新缓存，关闭流
     */
    private void shotSnap(RadioButton rb) {
        Bitmap curBitmap = null;

        rb.setEnabled(false);
        if (sdCardFile != null) {
            BufferedOutputStream bos;
            File captureFile = new File(picturePath + Generic.getSysNowTime()
                    + ".jpg");

            try {
                curBitmap = mjpegView.getBitmap();
                if(curBitmap != null){
                    bos = new BufferedOutputStream(
                            new FileOutputStream(captureFile));/*File-->输出流*/
                    curBitmap.compress(Bitmap.CompressFormat.JPEG, 80,
                            bos);/*压缩*/
                    bos.flush();
                    bos.close();
                    Generic.showMsg(this, "OK", true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Generic.showMsg(this, "请检查SD卡", true);
        }
        rb.setEnabled(true);
    }

    /**
     * 功能:浏览、删除图片
     * 先判断是否有SD卡
     * 有SD卡就设置将之前创建好的picturePath传递给ScanPicActivity，下面就关心ScanPicActivity
     *
     */
    private void scanPic() {
        if (sdCardFile != null) {
			/*可以设置Intent的putExtra，来传递数据*/
            startActivity(new Intent(this, ScanPicActivity.class).putExtra(
                    "picturePath", picturePath));
        } else {
            Generic.showMsg(this, "请检查SD卡", true);
        }
    }

    @Override
    /**
     * 当点击手机的返回键，将调用此方法
     * 跳到ExitActivity执行
     * @parm keyCode:键值
     * @parm event:按键动作
     * 如果是点击的返回键，新建并设置Intent，然后启动Activity的跳转，跳转成功返回真 失败返回假
     *
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Intent intent = new Intent();
            intent.setClass(this, ExitActivity.class);
            startActivity(intent);
            return true;
        }

        return false;
    }
}
