package com.example.shayne.myapplication;

import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;

import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.example.shayne.myapplication.io.MjpegInputStream;
import com.example.shayne.myapplication.tools.Generic;

/**
 * 应用程序执行时，该类首先被调用
 */
public class FlashActivity extends Activity {
    private Context mContext = this;
    private AutoCompleteTextView ipEdt = null;
    private EditText portEdt = null;
    private TextView hintTv = null;
    private DhcpInfo dpInfo = null;
    private WifiManager wifi = null;
    private InputStream is = null;
    private SharedPreferences sp = null;
    private Editor editor = null;
    private String port = "5432";/*用来保存获得用户输入的端口*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.flash);/* 设置布局为res/layout/flash.xml*/

        init();
        int state = wifi.getWifiState();/* 获得wifi当前状态 */

        if (state != WifiManager.WIFI_STATE_ENABLED) {
            /**
             * 为了程序的扩展性和可读性，单独在tools目录定义一个Generic类，它有很多方法
             * 1.有showMsg方法，用于控制显示时间来显示一个Toast
             * 2.有getSysNowTime方法，用于获取当前的系统时间
             * 3.有getSdCardFile方法，用于获取SD卡的绝对路径，成功返回File值，失败返回NULL
             * 4.有getConnectedIP方法，用于获取连接到wifi热点的所有的手机ip，成功返回ArrayList<String>型的容器
             * 5.有getShrinkedPic方法，用于获取照片的缩略图
             * 6.定义了一个DescendSortByIndex类：实现了整型比较器
             * 7.定义个DescendSortByTime类：实现了File比较器
             */
            Generic.showMsg(this, "请打开wifi", false);
            finish();
        } else
            autoConnect();
    }

    @Override
    /**
     * 调用finish方法时，这方法将被激发
     * 设置输入流为空，调用父类的onDestroy销毁资源
     */
    protected void onDestroy() {
        is = null;
        super.onDestroy();
    }

    private void init(){
        /**
         * 获取在本Activity要使用的控件和WiFi
         */
        hintTv = (TextView) findViewById(R.id.hintTv);
        ipEdt = (AutoCompleteTextView) findViewById(R.id.ip);
        portEdt = (EditText) findViewById(R.id.port);
        /**
         * 因为要用到WIFI和Internet所以在AndroidMenufest.xml 中添加如下权限
         * <uses-permission android:name="android.permission.INTERNET"/>
         * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
         * <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
         */
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        initSp();/*主要是方便查找以前登录成功了的IP*/
    }

    /**
     * 生成配置文件config，它在 /data/data/<package name>/shared_prefs/config.xml
     * 取出配置文件的ip用冒号隔开，并为自动完成列表设置适配器
     */
    private void initSp(){
        sp = getSharedPreferences("config", MODE_PRIVATE);
		/*创建好配置文件后，以后就可以用它的edit来操作配置文件了*/
        editor = sp.edit();
        String names[] = sp.getString("ip", "").split(":");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext,
                android.R.layout.simple_dropdown_item_1line, names);
        ipEdt.setAdapter(adapter);
    }

    /**
     * 自动连接
     * 先将获取到的wifi热点服务器地址和连接到wifi热点的设备的ip放入容器，启动连接线程扫描容器中的ip
     * @return
     */
    private void autoConnect() {
        ArrayList<String> addr = new ArrayList<String>();/*创建容器 用于存放ip*/

        dpInfo = wifi.getDhcpInfo();
        addr.add(int32ToIp(dpInfo.serverAddress));/* 把服务IP放入容器的尾部 */
        addr.addAll(Generic.getConnectedIP());/* Adds the objects in the specified collection to this ArrayList */

        /**
         * 为了在执行连接时 不会卡住UI，故采用异步任务方式，若读者想减缩程序，也可不使用异步任务
         */
        new ConnectTask().execute(addr.toArray(new String[addr.size()]));/*因为连接线程的执行方法必须String类型，所以要toArray*/
    }
    /**
     * 按照一定的格式返回输入的Ip
     * @param ip
     * @return
     */
    private String int32ToIp(int ip) {
        return (ip & 0xff) + "." + (ip >> 8 & 0xff) + "." + (ip >> 16 & 0xff)
                + "." + (ip >> 24 & 0xff);
    }

    /**
     * 手动连接
     * 为控件绑定监听器有2种方法
     * 1.给出布局文件并设置，findViewById()找到控件，调用API为其绑定相应监听器
     * 2.给出布局文件并设置，在布局文件里设置相应控件的OnClick，然后在源文件里具体实现相应控件的OnClick//本类用的就是这方法
     * 在layout目录下的flash.xml里声明了connectBtn的Button控件
     * 点击"连接"按钮将调用此方法
     * @param v
     */
    public void connectBtn(View v) {
        String ip = ipEdt.getText().toString();/*获得输入的IP*/
        port = portEdt.getText().toString();/*获得输入的端口*/

        //port不能为空
        if (!port.equals("")&&checkAddr(ip, Integer.valueOf(port))) {
            new ConnectTask().execute(ip);
        } else {
            Generic.showMsg(this, "请检查ip和port", true);
        }
    }

    /**
     * 分割的ip是4段，ip端口范围在1000-65535
     * @param ip
     * @param port
     * @return
     */
    private boolean checkAddr(String ip, int port) {
        if (ip.split("\\.").length != 4)
            return false;
        if (port < 1000 || port > 65535)
            return false;

        return true;
    }

    /**
     * 连接线程
     * 此类的作用是在后台线程里执行http连接，连接卡住不会影响UI运行，适合于运行时间较长但又不能影响前台线程的情况
     * 异步任务，有3参数和4步:onPreExecute()，doInBackground()，onProgressUpdate()，onPostExecute()
     * onPreExecute()：运行于UI线程，一般为后台线程做准备，如在用户接口显示进度条
     * doInBackground():当onPreExecute执行后，马上被触发，执行花费较长时间的后台运算，将返回值传给onPostExecute
     * onProgressUpdate():当用户调用 publishProgress()将被激发，执行的时间未定义，这个方法可以用任意形式显示进度
     * 一般用于激活一个进度条或者在UI文本领域显示logo
     * onPostExecute():当后台进程执行后在UI线程被激发，把后台执行的结果通知给UI
     * 参数一:运行于后台的doInBackground的参数类型
     * 参数二:doInBackground计算的通知给UI线程的单元类型，即运行于UI线程onProgressUpdate的参数类型，这里没用到
     * 参数三:doInBackground的返回值，将传给onPostExecute作参数
     * @author Administrator
     *
     */
    private class ConnectTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... params) {
            for (int i = 0; i < params.length; i++) {
                String ip = params[i];/* 取出每一个ip */

                if (ip.split("\\.").length == 4) {
                    /**
                     * 在浏览器观察画面时,也是输入下面的字符串网址
                     */
                    String action = "http://" + ip + ":"+ port + "/?action=stream";
                    is = http(action);
                    if (is != null) { /*第一次必须输入IP，下次登录时才可找到之前登录成功后的IP*/
                        writeSp(ip);
                        MjpegInputStream.initInstance(is);
                        break;
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (is != null) {
                /**
                 * Intent是Android特有的东西，可以在Intent指定程序要执行的动作(比如:view,edit,dial)
                 * 都准备好程序执行该工作所需要的材料后，只要调用startActivity，Android系统会自动寻找最符合你指定要求的应用程序
                 * 并执行该程序
                 */
                startActivity(new Intent(FlashActivity.this, MainActivity.class));
                finish();/*结束本Activity*/
            } else{
                hintTv.setText(getResources()
                        .getString(R.string.connect_failed));
                Generic.showMsg(mContext, "连接失败", true);
            }

            super.onPostExecute(result);
        }

        /**
         * 功能：http连接
         * Android提供两种http客户端， HttpURLConnection 和 Apache HTTP Client，它们都支持HTTPS，能上传和下载文件
         * 配置超时时间，用于IPV6和 connection pooling， Apache HTTP client在Android2.2或之前版本有较少BUG
         * 但在Android2.2或之后，HttpURLConnection是更好的选择，在这里我们用的是 Apache HTTP Client
         * 凡是对IO的操作都会涉及异常，所以要try和catch
         * @param url
         * @return InputStream
         */
        private InputStream http(String url) {
            HttpResponse res;
            DefaultHttpClient httpclient = new DefaultHttpClient();/*创建http客户端，才能调用它的各种方法*/
            httpclient.getParams().setParameter(
                    CoreConnectionPNames.CONNECTION_TIMEOUT, 500);/*设置超时时间*/

            try {
                HttpGet hg = new HttpGet(url);/*这是GET方法的http API， GET方法是默认的HTTP请求方法*/
                res = httpclient.execute(hg);
                return res.getEntity().getContent(); // 从响应中获取消息实体内容
            } catch (IOException e) {
            }

            return null;
        }

    }

    /**
     * 更新SharedPreferences
     * 1.先判断ip是否有"ip"值，没有就将传进来的data赋值给ip
     * 2.ip有值就取出，然后用冒号分隔开
     * 3.sp数组只能存放10组ip，如果超过了10组，先清零配置文件再更新
     * 4.遍历数组，如果已有当前登录成功的ip，则返回
     * 5.数组里不包含登录成功的ip，则将当前登录成功的ip添加至sp数组并提交
     * @param ip
     */
    private void writeSp(String data) {
        if(!sp.contains("ip")){
            editor.putString("ip", data);
            editor.commit();
            return;
        }

        /**
         * 配置文件里有ip，表示之前登录成功了
         */
        String ip = sp.getString("ip", "");
        String[] ips = ip.split(":");

        if(ips.length >= 10){
            editor.clear();
            editor.commit();
            editor.putString("ip", data);
            editor.commit();
            return;
        }

        for(int i=0; i<ips.length; i++){
            if(ips[i].equals(data))
                return;
        }
        editor.putString("ip", data+":"+ip);/*放在以前成功了的ip的前面*/
        editor.commit();
    }

    /**
     * 自动完成框的下拉选项
     * 当点击"history_user"ImageView控件时将调用该方法
     * 这里只是具体实现xml文件的Onclick
     */
    public void showDropDown(View v){
        ipEdt.showDropDown();
    }


}
