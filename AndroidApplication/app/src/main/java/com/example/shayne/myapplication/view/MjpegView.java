package com.example.shayne.myapplication.view;

/**
 * Created by shayne on 17-5-16.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.shayne.myapplication.io.MjpegInputStream;

import java.io.IOException;

/**
 * 此类继承了SurfaceView实现了SurfaceHolder.Callback接口
 * SurfaceView是视图类(view)的继承类，这个视图里内嵌入了一个专门用于绘制的Surface	，可以控制这个Surface的格式和尺寸
 * SurfaceView控制这个Surface的绘制位置
 * surface是纵深排序(Z-ordered)的，这表明它总在自己所在窗口的后面。surfaceview提供了一个可见区域
 * 只有在这个可见区域内 的surface部分内容才可见，可见区域外的部分不可见。surface的排版显示受到视图层级关系的影响
 * 它的兄弟视图结点会在顶端显示，这意味者 surface的内容会被它的兄弟视图遮挡，这一特性可以用来放置遮盖物(overlays)(例如，文本和按钮等控件)
 * 可以通过SurfaceHolder接口访问这个surface，getHolder()方法可以得到这个接口
 * surfaceview变得可见时，surface被创建；surfaceview隐藏前，surface被销毁；这样能节省资源。如果你要查看 surface被创建和销毁的时机
 * 可以重载surfaceCreated(SurfaceHolder)和 surfaceDestroyed(SurfaceHolder)
 * surfaceview的核心在于提供了两个线程:UI线程和渲染线程，这里应注意:
 * 1> 所有SurfaceView和SurfaceHolder.Callback的方法都应该在UI线程里调用，一般来说就是应用程序主线程，渲染线程所要访问的各种变量应该作同步处理。
 * 2> 由于surface可能被销毁，它只在SurfaceHolder.Callback.surfaceCreated()和 SurfaceHolder.Callback.surfaceDestroyed()之间有效，
 * 所以要确保渲染线程访问的是合法有效的surface
 * 整个过程:继承SurfaceView并实现SurfaceHolder.Callback接口 ----> SurfaceView.getHolder()获得SurfaceHolder对象(Surface控制器)
 * ---->SurfaceHolder.addCallback(callback)添加回调函数---->SurfaceHolder.lockCanvas()获得Canvas对象并锁定画布
 * ----> Canvas绘画 ---->SurfaceHolder.unlockCanvasAndPost(Canvas canvas)结束锁定画图，并提交改变，将图形显示。
 */
public class MjpegView extends SurfaceView implements SurfaceHolder.Callback {
    /*fps显示位置*/
    public final static int POSITION_UPPER_LEFT = 9;
    public final static int POSITION_UPPER_RIGHT = 3;
    public final static int POSITION_LOWER_LEFT = 12;
    public final static int POSITION_LOWER_RIGHT = 6;
    /*图像显示模式*/
    public final static int STANDARD_MODE = 1;//标准尺寸
    public final static int KEEP_SCALE_MODE = 4;//保持宽高比例
    public final static int FULLSCREEN_MODE = 8;//全屏

    private Context mContext = null;
    private MjpegViewThread mvThread = null;
    private MjpegInputStream mIs = null;
    private Paint overlayPaint = null;//用于fps涂层绘画笔
    private boolean bIsShowFps = true;
    private boolean bRun = false;
    private boolean bsurfaceIsCreate = false;
    private int overlayTextColor;
    private int overlayBackgroundColor;
    private int ovlPos;
    private int dispWidth;//MjpegView的宽度
    private int dispHeight;//MjpegView的高度
    private int displayMode;//覆盖模式

    public MjpegView(Context context) {
        super(context);
        init(context);
    }

    /**
     * 因为在res/layout目录下的main.xml中作为自定义的控件使用了这个类，所以需要给此类提供带有属性形参的构造函数
     * 当在MainActivity通过ID找到这自定义的控件时，该构造函数将被调用，所以将该构造函数设为public
     * @param context
     * @param attrs
     */
    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    /**
     * 类的私有方法
     * 1.获得Surface控制器，为Surface控制器添加回调接口
     * 2.新建渲染线程MjpegViewThread
     * 3.新建覆盖画笔，设置文本的对齐方式、文本长度、字体、画笔文本颜色、画笔背景
     * 4.设置覆盖动态文本的覆盖位置 //如果你只需要实现监控画面的功能，3和4步可以省略
     * 5.设置MjpegView显示模式
     * @param context
     */
    private void init(Context context) {
        mContext = context;
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        mvThread = new MjpegViewThread(holder, context);
        setFocusable(true);
        overlayPaint = new Paint();
        overlayPaint.setTextAlign(Paint.Align.LEFT);
        overlayPaint.setTextSize(12);
        overlayPaint.setTypeface(Typeface.DEFAULT);

        overlayTextColor = Color.RED;
        overlayBackgroundColor = Color.TRANSPARENT;
        ovlPos = MjpegView.POSITION_UPPER_RIGHT;
        displayMode = MjpegView.KEEP_SCALE_MODE;

    }
    /**
     *  Surface的任何结构性结构性的改变(如格式，大小)将激发此方法
     *  主要调用渲染线程的setSurfaceSize来设置Surface的宽和高
     */
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        mvThread.setSurfaceSize(w, h);
    }
    /**
     * Surface被销毁之前将激发此方法，这里只设置标记位，表示Surface“被销毁了”
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        bsurfaceIsCreate = false;
    }
    /**
     * Surface被第一次创建后将激发此方法，这里只设置标记位，表示Surface“被创建了”
     */
    public void surfaceCreated(SurfaceHolder holder) {
        bsurfaceIsCreate = true;
    }
    /**
     * setFps，getFps，set source都在MaiActivity使用
     * @param b
     */
    public void setFps(boolean b) {
        bIsShowFps = b;
    }

    public boolean getFps(){
        return bIsShowFps;
    }

    public void setSource(MjpegInputStream source) {
        mIs = source;
    }

    /**
     * 开始播放线程
     * 设置标记，表示“Surface被创建了”，然后调用渲染线程的的run方法启动渲染
     */
    public void startPlay() {
        if (mIs != null) {
            bRun = true;
            mvThread.start();
        }
    }

    /**
     * 停止播放线程
     * 1.先设置标记，表示"停止播放"
     * 2.等待播放线程的退出
     * 3.关闭输入流
     */
    public void stopPlay() {
        bRun = false;
        boolean retry = true;
        while (retry) {
            try {
                mvThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }

        //线程停止后关闭Mjpeg流(很重要)
        mIs.closeInstance();
    }
    /**
     * mjpegview的获取位图方法，调用渲染线程的获取位图方法
     * @return
     */
    public Bitmap getBitmap(){
        return mvThread.getBitmap();
    }

    /**
     * 设置显示模式，在MainActivity的initview调用
     * @param s
     */
    public void setDisplayMode(int s) {
        displayMode = s;
    }
    /**
     * 既然有设置显示模式，就应该也有获得显示模式，这是java在设置方法方面的风格
     * @return
     */
    public int getDisplayMode() {
        return displayMode;
    }
    /**
     * 此渲染线程类在主类上是重点，应该重点掌握
     * @author Administrator
     *
     */
    public class MjpegViewThread extends Thread {
        private SurfaceHolder mSurfaceHolder = null;
        private int frameCounter = 0;
        private long start = 0;
        private Canvas c = null;
        private Bitmap overlayBitmap = null;
        private Bitmap mjpegBitmap = null;
        private PorterDuffXfermode mode = null;
        /**
         * 用一个变量来保存传进来的surfaceHolder
         * 新建一个目的图层和覆盖图层的相交模式，mjpegview为目的图层，覆盖图层为右上角的动态"文本"
         * mode在calculateFps方法里使用
         * @param surfaceHolder:Surfaceview控制器
         * @param context : 上下文环境
         */
        public MjpegViewThread(SurfaceHolder surfaceHolder, Context context) {
            mSurfaceHolder = surfaceHolder;
            mode = new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);/*相交时动态文本覆盖mjpegview*/
        }

        public Bitmap getBitmap(){
            return mjpegBitmap;
        }

        /**
         * 计算图像尺寸
         * @param bmw bitmap宽
         * @param bmh bitmap高
         * @return 图像矩阵
         */
        private Rect destRect(int bmw, int bmh) {
            int tempx;
            int tempy;
            /**
             * 显示模式只会在全屏和半屏模式之间切换，根本不会进入STANDARD_MODE模式，故下面的if分支可以去掉
             */
            if (displayMode == MjpegView.STANDARD_MODE) {
                tempx = (dispWidth / 2) - (bmw / 2);
                tempy = (dispHeight / 2) - (bmh / 2);
                return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
            }
            /**
             * 一开始，程序处于KEEP_SCALE_MODE模式，表示半屏显示画面
             */
            if (displayMode == MjpegView.KEEP_SCALE_MODE) {
                float bmasp = (float) bmw / (float) bmh;
                bmw = dispWidth;
                bmh = (int) (dispWidth / bmasp);/*宽是手机屏幕的一半*/
                if (bmh > dispHeight) {
                    bmh = dispHeight;
                    bmw = (int) (dispHeight * bmasp);
                }
                tempx = (dispWidth / 2) - (bmw / 2);
                tempy = (dispHeight / 2) - (bmh / 2);
                /**
                 * Rect(左边，顶边，右边，下边)，功能是绘制一个特定坐标的矩形
                 * 简单说就是左上角坐标为(0，0),右下角坐标为(bmw，bmh)
                 */
                return new Rect(0, 0, bmw + 0, bmh + 0);
            }
            /**
             * 如果显示模式为全屏，则全屏显示画面
             * dispWidth和dispHeight在下面的setSurfaceSize方法使用，它们表示mjpegview的宽和高
             */
            if (displayMode == MjpegView.FULLSCREEN_MODE)
                return new Rect(0, 0, dispWidth, dispHeight);
            return null;
        }
        /**
         * 当mjpegview发生任何结构性的改变时，将激发此方法，前面也提到，渲染线程使用的各种变量需做同步处理
         * synchronized内的就是同步代码块，为了防止线程之间对临界资源的竞争
         * @param width
         * @param height
         */
        public void setSurfaceSize(int width, int height) {
            synchronized (mSurfaceHolder) {
                dispWidth = width;
                dispHeight = height;
            }
        }
        /**
         * 此方法被calculateFps使用，calculateFps又被渲染线程的run方法使用
         * 功能是返回一个位图
         * @param p:覆盖"文本"用的画笔
         * @param text:要绘制的字符 如:帧
         * @return bm
         */
        private Bitmap makeFpsOverlay(Paint p, String text) {
            int nWidth, nHeight;

            Rect b = new Rect();
            //int  a = b.left ;
            /**
             * 功能是获得从原点开始，字符围绕的最小的矩形
             * text：字符
             * 0：表示第一个字符
             * text.length:测量的最后一个字符
             * b:用于存放获得的字符矩形
             * 获得了text的边界后就可以得到矩形的宽和高
             */
            p.getTextBounds(text, 0, text.length(), b);
            nWidth = b.width() + 2;
            nHeight = b.height() + 2;
            /**
             * 每一个像素4字节，根据上面获得的宽和高返回一个位图
             */
            Bitmap bm = Bitmap.createBitmap(nWidth, nHeight,
                    Bitmap.Config.ARGB_8888);
            /**
             * Canvas :画布，这是图像处理的基本单元
             * 画图时，需要4个重要的元素：
             * 1.操作像素的位图
             * 2.绘图到位图的画布
             * 3.矩形
             * 4. 描述颜色和绘制风格的画笔
             * Canvas(bm):构造出一个要绘制到位图的画布
             */
            Canvas c = new Canvas(bm);
            /**
             * Paint类介绍
             * Paint即画笔，在绘图过程中起到了极其重要的作用，画笔主要保存了颜色，
             * 样式等绘制信息，指定了如何绘制文本和图形，画笔对象有很多设置方法，
             * 大体上可以分为两类，一类与图形绘制相关，一类与文本绘制相关。
             *
             * 1.图形绘制
             * setColor(int color);
             * 设置绘制的颜色，使用颜色值来表示，该颜色值包括透明度和RGB颜色。
             * setDither(boolean dither);
             * setXfermode(Xfermode xfermode);
             * 设置图形重叠时的处理方式，如合并，取交集或并集，经常用来制作橡皮的擦除效果
             *
             * 2.文本绘制
             * setFakeBoldText(boolean fakeBoldText);
             * 模拟实现粗体文字，设置在小字体上效果会非常差
             * setSubpixelText(boolean subpixelText);
             * 设置该项为true，将有助于文本在LCD屏幕上的显示效果
             *
             * setTextAlign(Paint.Align align);
             * 设置绘制文字的对齐方向
             * setTextSize(float textSize);
             * 设置绘制文字的字号大小
             * setTypeface(Typeface typeface);
             * 设置Typeface对象，即字体风格，包括粗体，斜体以及衬线体，非衬线体等
             */

            p.setColor(overlayBackgroundColor);// 背景颜色
            c.drawRect(0, 0, nWidth, nHeight, p);/*绘制矩形*/
            p.setColor(overlayTextColor);// 文字颜色
            /**
             * 画布的绘制文字方法
             * test:要绘制的字符
             * -b.left:字符起始位置的x坐标，这里是矩形的左边
             * (nHeight / 2) - ((p.ascent() + p.descent()) / 2) + 1:字符起始位置的y坐标
             * p:用到的画笔
             * 关于涉及的矩形属性可看博客  http://mikewang.blog.51cto.com/3826268/871765
             */
            c.drawText(text, -b.left + 1,
                    (nHeight / 2) - ((p.ascent() + p.descent()) / 2) + 1, p);

            return bm;
        }

        /**
         * 重头戏
         * 如果线程是运行的，SurfaceView也创建了的
         * 则锁定画布com/mjpeg/io/MjpegInputStream.java中的readMjpegFrame方法获得mjpeg视频流的内容
         * mjpeg视频的内容就是类位图，然后根据类位图绘制矩形，再绘制相应的位图，这个位图才是我们需要的
         * 如果设置了帧率文本，就在mjpegview上覆盖，最后解锁画布
         */
        public void run() {
            start = System.currentTimeMillis();
            Rect destRect;
            Paint p = new Paint();
            //		String fps = "";
            while (bRun) {
                if (bsurfaceIsCreate) {
                    c = mSurfaceHolder.lockCanvas();
                    try {
                        mjpegBitmap = mIs.readMjpegFrame();/*调用Inputstrean的方法*/
						/*同步图像的宽高设置*/
                        synchronized (mSurfaceHolder) {
                            destRect = destRect(mjpegBitmap.getWidth(),
                                    mjpegBitmap.getHeight());
                        }
                        /**
                         * 当主activity点击相册和设置跳转时，Surfaceview被销毁，此时c将为空
                         */
                        if(c != null){
                            c.drawPaint(new Paint());
                            c.drawBitmap(mjpegBitmap, null, destRect, p);
                            if (bIsShowFps)
                                calculateFps(destRect, c, p);
                            mSurfaceHolder.unlockCanvasAndPost(c);
                        }
                    } catch (IOException e) {
                    }
                }else {
                    try {
                        Thread.sleep(500);//线程休眠，让出调度
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        /**
         * 使用前面的方法，绘制出“显示帧率”文本，效果为"i帧"，i自增
         * @param destRect
         * @param c
         * @param p
         */
        public void calculateFps(Rect destRect, Canvas c, Paint p) {
            int width;
            int height;
            String fps;

            p.setXfermode(mode);/* 设置两个画面相交时的模式*/
            if (overlayBitmap != null) {
                /**
                 * 计算好文本的宽和高
                 * 然后调用画布的绘制位图方法绘图
                 */
                height = ((ovlPos & 1) == 1) ? destRect.top
                        : destRect.bottom - overlayBitmap.getHeight();
                width = ((ovlPos & 8) == 8) ? destRect.left
                        : destRect.right - overlayBitmap.getWidth();
                c.drawBitmap(overlayBitmap, width, height, null);
            }
            p.setXfermode(null);
            frameCounter++;
            /**
             * currentTimeMillis表示系统从January 1, 1970 00:00:00.0 UTC开始的毫秒数
             * start在前面已经设置好，表示渲染线程开始的系统时间
             */
            if ((System.currentTimeMillis() - start) >= 1000) {
                fps = frameCounter+ "fps";
                start = System.currentTimeMillis();
                overlayBitmap = makeFpsOverlay(overlayPaint, fps);/*真正的绘制这个"文本"*/
                frameCounter = 0;
            }
        }


    }

}