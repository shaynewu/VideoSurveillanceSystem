package com.example.shayne.myapplication.pic;

/**
 * Created by shayne on 17-5-16.
 */

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.shayne.myapplication.R;

/**
 * 一继承BaseAdapter，就需要覆盖其中的方法
 * BaseAdapter是Android应用程序中经常用到的基础数据适配器，它是继承自接口类Adapter
 * 主要用途是将一组数据传到像ListView、Spinner、Gallery及GridView等UI显示组件中
 * 灵活性较高，自定义Adapter子类，就需要实现它的多个方法，其中最重要的是getView()方法
 * 它是将获取数据后的View组件(如ListView中每一行里的TextView、Gallery中的每个ImageView)返回
 */

public class PicAdapter extends BaseAdapter{
    private Context mContext = null;
    /**
     * 在实际开发中LayoutInflater类非常有用,它的作用类似于findViewById()
     * 不同点的是LayoutInflater是用来找layout下xml布局文件，并且实例化！
     * 而findViewById()是找具体xml下的具体widget控件(如:Button,TextView等)
     */
    private LayoutInflater mInflater = null;
    private List<PicEntity> picList = null;
    /**
     * 适配器的构造方法，当new一个适配器时将激发此方法
     * @param context
     */
    public PicAdapter(Context context) {
        mContext = context;
        /**
         *有两种获得LayoutInflater的方法,它们本质是一样的
         *(1)通过SystemService获得:
         *LayoutInflater inflater=(LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
         *(2)从给定的context中获取:
         *LayoutInflater inflater = LayoutInflater.from(Activity.this);// 本例用的就是这方法
         *
         */
        this.mInflater = LayoutInflater.from(mContext);/*从本类中获得LayoutInflater*/
        picList = new ArrayList<PicEntity>();
    }

    //得到listview数据
    public List<PicEntity> getData(){
        return picList;
    }

    //添加数据到listview
    public void addData(List<PicEntity> l){
        picList.addAll(l);
    }

    //得到选中的Item集合，里面的内容为Item对应的Position
    public List<Integer> getSelectItem(){
        List<Integer> selectItemList = new ArrayList<Integer>();

        for(int j=0; j<picList.size(); j++){
            if(picList.get(j).getIsSelect())
                selectItemList.add(j);
        }

        return selectItemList;
    }

    @Override
    /**
     * 本例中没用到，但需要设置 要不然报错
     */
    public int getCount() {
        return picList.size();
    }

    @Override
    /**
     * 也没用到
     */
    public Object getItem(int position) {
        return picList.get(position);
    }

    @Override
    /**
     * 返回条目的位置
     */
    public long getItemId(int position) {
        return position;
    }

    @Override
    /**
     * 本类重点，功能是获得View
     * 1.根据xml 找到view，找到控件
     * 2.一开始无View，找到控件实体，为View设置Tag
     * 3.获得Tag，根据它来实例化listview中的控件
     */
    public View getView(final int position, View convertView, ViewGroup parent) {
        viewHolder vHolder = null;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.pic_listview_item, null);/*将LayOut文件转化成View*/
            vHolder = new viewHolder();
            vHolder.imageView = (ImageView) convertView.findViewById(R.id.image);
            vHolder.textView = (TextView) convertView.findViewById(R.id.picname);
            vHolder.checkBox = (CheckBox) convertView.findViewById(R.id.chbox);
            convertView.setTag(vHolder);
        }else {
            vHolder=(viewHolder) convertView.getTag();
        }
        /**
         * 获得vHolder后， 就可以使用了
         */
        vHolder.imageView.setImageBitmap(picList.get(position).getBm());
        vHolder.textView.setText(picList.get(position).getName());
        vHolder.checkBox.setChecked(picList.get(position).getIsSelect());
        /**
         * 为checkbox绑定监听器，并覆盖其中的onclick方法
         */
        vHolder.checkBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                boolean state = ((CheckBox)arg0).isChecked();/*获得checkbox的选中状态*/
                picList.get(position).setIsSelect(state);/*根据选中状态来设置checkbox*/
            }
        });

        return convertView;
    }
    /**
     * 为方便，将listview的控件集中一起，自定义成一类
     */
    public final class viewHolder{
        public ImageView imageView;
        public TextView textView;
        public CheckBox checkBox;
    }

}
