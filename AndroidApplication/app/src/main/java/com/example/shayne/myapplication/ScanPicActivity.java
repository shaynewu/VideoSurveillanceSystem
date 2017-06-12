package com.example.shayne.myapplication;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.security.auth.callback.Callback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.example.shayne.myapplication.pic.PicAdapter;
import com.example.shayne.myapplication.pic.PicEntity;
import com.example.shayne.myapplication.tools.Generic;

/**
 * 照片浏览类
 * 用来填充listview并对listview的一些操作
 * @author Administrator
 */
public class ScanPicActivity extends Activity implements OnItemClickListener {
    private static int UPDATE_DATA = 1;
    private Context mContext = this;
    private String picturePath = "";/*接收来自Mainactivity传入的图片路径*/
    private PicAdapter mAdapter = null;
    private ListView mListView = null;
    /**
     * 创建一个Handler，用于处理接收到的消息
     * 当接收到的消息为UPDATE_DATA时，更新照片
     * Handler一般在类的最开始就新建好
     */
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if(msg.what == UPDATE_DATA)//更新照片
            /**
             * mAdapter是继承了BaseAdapter，它有notifyDataSetChanged方法
             * 这方法能通知监测该图片资源的caller，caller使用这些资源时需要更新
             */
                mAdapter.notifyDataSetChanged();
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /**
         * pic_listview：初始化好listview窗口，在res/layout/pic_listview.xml定义
         */
        setContentView(R.layout.pic_listview);

        init();
    }

    /**
     * 1.根据id找到listview控件
     * 2.获得Intent的picturePath字符串,并设置listview标题为picturePath
     * 3.为图片新建一个Adapter
     * 4.将Adapter绑定到listview，也为listview绑定一个监听器
     * 5.调用加载线程的执行方法，加载图片
     */
    private void init() {
        mListView = (ListView) findViewById(R.id.list);
        picturePath = getIntent().getStringExtra("picturePath");
        setTitle(picturePath);
        if (!picturePath.equals("")) {
            mAdapter = new PicAdapter(mContext);
            mListView.setAdapter(mAdapter);
            mListView.setOnItemClickListener(ScanPicActivity.this);
            new LoadPicTask().execute();
        }
        else{
            Generic.showMsg(mContext, "请检查SdCard", true);
        }
    }

    /**
     * 当点击listview的条目时，将调用该方法
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        /**
         * 每一个File都是一个文件对象
         * 1.根据textView创建相应的File
         * 2.创建Intent，设置它的Action
         * 当点击条目的时候，即打开图片
         */
        File f = new File(picturePath
                + ((PicAdapter.viewHolder) view.getTag()).textView.getText());/*前面PicAdapter已经设置好View的Tag*/
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);/*设置intent的Action属性,搜索应用时会找到*/
        intent.setDataAndType(Uri.fromFile(f), "image/*");/*设置Intent的数据来源和数据类型，表示默认打开图片*/
        startActivity(intent);
    }

    @Override
    /**
     * 功能:点击手机的"MENU"键，弹出menu.xml定义的"批量删除"
     * 关于MenuInflater:
     * MenuInflater是用来实例化Menu目录下的Menu布局文件的。传统意义上的菜单定义需要Override Activity的onCreateOptionsMenu
     * 然后在里面调用Menu.add把Menu的一个个item加进来，比较复杂,而通过使用MenuInflater可以把Menu的构造直接放在Menu布局文件中
     * 真正实现模型（Model）与视图（View）的分离，程序也看着清爽多了,与LayoutInflater相比，MenuInflater的用法简单多了
     * 首先，MenuInflater获取方法只有一种：Activity.getMenuInflater()
     * 其次，MenuInflater.inflater(int menuRes,Menu menu)(这里不代表inflater就是static方法，可以这样调用，只是为了描述方便)的返回值是void型
     * 这就决定了MenuInflater.inflater后就没有后续操作了。这说明通过这种方式把Menu布局文件写好后就不能在程序中动态修改了
     * 而不像LayoutInflater.inflater那样，返回值是View型，可以进行后续的进一步操作
     * 另外，MenuInflater只有一个void inflater(int menuRes,Menu menu)非构造方法
     * Menu布局文件中的icon资源是Android自带的，通过“＠android：drawable/"获取到
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    /**
     * 当你要删除照片 但是没有选择要删除哪些照片，就会提示“请选择找照片”
     * 如果选中了欲删除的照片，弹出“删除选择的图片吗？”对话框，点确定 执行删除动作，反之不删
     * 这方法表示点击menu菜单后的操作
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mAdapter == null || mAdapter.getSelectItem().size() == 0) {
            Generic.showMsg(mContext, "请选择找照片", true);
            return true;
        }

        /**
         * 创建对话框，并实现onClick方法
         */
        new AlertDialog.Builder(this).setMessage("删除选择的图片吗？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (delPics()) /*删除照片成功*/
                            Generic.showMsg(mContext, "删除成功", true);
                        else
                            Generic.showMsg(mContext, "删除失败", true);
                        mAdapter.notifyDataSetChanged();// 发送给Handler
                    }
                }).setNegativeButton("取消", null).create().show();

        return true;
    }

    /**
     * 删除照片操作
     * 1.获得存放实体的容器
     * 2.获得总条目数
     * 3.排序(方便查找)
     * 4.遍历容器，删除选中的照片
     * 5.移除容器里的实体
     * @return
     */
    private boolean delPics() {
        List<PicEntity> picList = mAdapter.getData();/*加载线程会添加数据到list后,获得listview的存放图片实体的list类型的容器*/
        List<Integer> selectItemList = null;

        selectItemList = mAdapter.getSelectItem();/*获取listview的条目个数*/

        sortDescendByIndex(selectItemList);
        for (int i = 0; i < selectItemList.size(); i++) { /*遍历所有条目*/
            PicEntity entity = picList.get(selectItemList.get(i));/*获得picture实体*/

            if (!new File(picturePath + "/" + entity.getName()).delete())/*删除*/
                return false;

            if (!picList.remove(entity))/*移除*/
                return false;
        }

        return true;
    }

    /**
     * 按索引号降序排序
     *
     * @param list:整型容器
     */
    private void sortDescendByIndex(List<Integer> list) {
        Collections.sort(list, new Generic.DescendSortByIndex());
    }

    /**
     * 按索最后修改时间降序排序
     *
     * @param list:File容器
     */
    private void sortDescendByTime(List<File> list) {
        Collections.sort(list, new Generic.DescendSortByTime());
    }

    /**
     * 加载照片线程
     *
     * @author Administrator
     *
     */
    private class LoadPicTask extends AsyncTask<String, Integer, String> {
        private int step = 5;
        private List<File> picList = new ArrayList<File>();

        @Override
        /**
         * 1.罗列出picturepath的File个体
         * 2.遍历picFile数组，将数组里每一个File放入list容器
         * 3.根据文件的修改时间排序
         */
        protected void onPreExecute() {
            File[] picFile = new File(picturePath).listFiles();

            for(int i=0; i<picFile.length; i++)
                picList.add(picFile[i]);

            sortDescendByTime(picList);
            super.onPreExecute();
        }

        /**
         * 线程分页加载数据
         * 1.新建存放File对象和PicEntity的容器
         * 2.获得list容器的数据并将数据存放到File容器
         * 3.如果File有数据，根据File容器找到picture实体，清空容器
         * 4.将获得的picture实体放入list
         */
        @Override
        protected String doInBackground(String... params) {
            //用于存放分页file数据
            List<File> tmpList = new ArrayList<File>();
            //用于存放分页得到的pic实体
            List<PicEntity> tmpEntity = new ArrayList<PicEntity>();
            int sum = picList.size();// 5
            int time = sum / step + 1;// 5 / 5 + 1 = 2
            /**
             * 加载time次，一次加载step张
             * 第一个for加载time次
             * 第二个for：time=0，加载0-4张，time=1，加载5-9次；依次类推
             */
            for(int j=0; j<time; j++){
                for(int i=0; (i<step)&&(j*step+i<sum); i++)
                    tmpList.add(picList.get(j*step+i));

                if(tmpList.size() > 0){
                    tmpEntity = getPicEntity(tmpList);
                    tmpList.clear();
                    mAdapter.addData(tmpEntity);

                    handler.sendEmptyMessage(UPDATE_DATA);/*有图片，发送消息*/
                }
            }
            return null;
        }

        /**
         * 得到分页file对应的pic实体集合
         * 1.获得传进来的list<File>类型的容器的File
         * 2.如果是File，获得文件名
         * 3.如果文件名是与特定后缀结束的，创建pictrue实体并添加至List
         * @param path
         * @return
         */
        private List<PicEntity> getPicEntity(List<File> picList) {
            List<PicEntity> list = new ArrayList<PicEntity>();/*创建存放PicEntity的list*/

            for (int i = 0; i < picList.size(); i++) {
                File f = picList.get(i);
                if (f.isFile()) {
                    String fileName = f.getName();

                    if (fileName.endsWith(".jpg") || fileName.endsWith("jpeg")
                            || fileName.endsWith(".png"))
                        list.add(new PicEntity(fileName, Generic.getShrinkedPic(f)));
                }
            }

            return list;
        }

    }
}
