package com.example.shayne.myapplication.pic;

/**
 * Created by shayne on 17-5-16.
 */

import android.graphics.Bitmap;

/**
 * picture实体类
 * 1构造函数
 * 设置位图/获得 位图方法，设置文件名和获得文件名方法
 * 设置被选中和获得是否被选中的状态方法
 * @author Administrator
 *
 */
public class PicEntity {
    private String name;//文件名
    private Bitmap bm;//缩略图
    private boolean bIsSelect = false;//item是否被选中

    public PicEntity(String name, Bitmap bm) {
        this.name = name;
        this.bm = bm;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Bitmap getBm() {
        return bm;
    }

    public void setBm(Bitmap bm) {
        this.bm = bm;
    }

    public boolean getIsSelect() {
        return bIsSelect;
    }

    public void setIsSelect(boolean bIsSelect) {
        this.bIsSelect = bIsSelect;
    }
}
