package com.example.shayne.myapplication.io;

/**
 * Created by shayne on 17-5-16.
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Properties;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcelable;
import android.util.Log;
/**
 * 该类继承了DataInputStream实现了Serializable接口
 * 1. 实例化流,获取初始化流和关闭实例流的方法
 * 2. 一个构造函数
 * 3. 一个根据帧数据大小获得位图方法
 */

public class MjpegInputStream extends DataInputStream implements Serializable{
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    /**
     * 用UE打开发现 每一个jpg格式的图片 开始两字节都是 0xFF,0xD8
     */
    private final byte[] SOI_MARKER = { (byte) 0xFF, (byte) 0xD8 };
//	private final byte[] EOF_MARKER = { (byte) 0xFF, (byte) 0xD9 };
    /**
     * 表示服务器发给客户端的一帧数据的长度
     */
    private final String CONTENT_LENGTH = "Content-Length";
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 40000 + HEADER_MAX_LENGTH;
    private int mContentLength = -1;
    private static MjpegInputStream mis = null;
    /**
     * 调用该类的构造方法 创建MjpegInputStream流
     * @param is
     */
    public static void initInstance(InputStream is){
        if(mis == null)
            mis = new MjpegInputStream(is);

    }
    /**
     * 获得创建的mjpegInputsteam流
     * @return
     */
    public static MjpegInputStream getInstance(){
        if(mis != null)
            return mis;

        return null;
    }
    /**
     * 因为mpjeginputstream继承了datainputstream
     * 所以可以调用mpjeginputstream的关闭流方法
     */
    public static void closeInstance(){
        try {
            mis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mis = null;
    }

    private MjpegInputStream(InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }
    /**
     * 在数据流里面找SOI_MARKER={(byte)0xFF,(byte) 0xD8}
     * 所有对IO流的操作都会抛出异常
     * @param in
     * @param sequence
     * @return
     * @throws IOException
     */
    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence)
            throws IOException {
        int seqIndex = 0;
        byte c;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {// 0 1 2 3
            c = (byte) in.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                seqIndex++;
                if (seqIndex == sequence.length)//2
                    return i + 1;//3
            } else
                seqIndex = 0;
        }
        return -1;
    }
    /**
     * 此方法功能是找到索引0xFF,0XD8在字符流的位置
     * 整个数据流形式：http头信息 帧头(0xFF 0xD8) 帧数据 帧尾(0xFF 0xD9)
     * 1、首先通过0xFF 0xD8找到帧头位置
     * 2、帧头位置前的数据就是http头，里面包含Content-Length，这个字段指示了整个帧数据的长度
     * 3、帧头位置后面的数据就是帧图像的开始位置
     * @param in
     * @param sequence
     * @return
     * @throws IOException
     */
    private int getStartOfSequence(DataInputStream in, byte[] sequence)
            throws IOException {
        int end = getEndOfSeqeunce(in, sequence);
        return (end < 0) ? (-1) : (end - sequence.length);
    }
    /**
     * 从http的头信息中获取Content-Length，知道一帧数据的长度
     * @param headerBytes
     * @return
     * @throws IOException
     * @throws NumberFormatException
     */
    private int parseContentLength(byte[] headerBytes) throws IOException,
            NumberFormatException {
        /**
         * 根据字节流创建ByteArrayInputStream流
         * Properties是java.util包里的一个类，它有带参数和不带参数的构造方法，表示创建无默认值和有默认值的属性列表
         * 根据流中的http头信息生成属性文件，然后找到属性文件CONTENT_LENGTH的value，这就找到了要获得的帧数据大小
         * 创建一个 ByteArrayInputStream，使用 headerBytes作为其缓冲区数组
         */
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();/*创建一个无默认值的空属性列表*/
        props.load(headerIn);/*从输入流中生成属性列表（键和元素对）。*/
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));/*用指定的键在此属性列表中搜索属性。*/
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public Bitmap readMjpegFrame() throws IOException {
        mark(FRAME_MAX_LENGTH);/*流中当前的标记位置*/
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();/*将缓冲区的位置重置为标记位置*/
        byte[] header = new byte[headerLen];

        readFully(header);/*会一直阻塞等待，直到数据全部到达(数据缓冲区装满)*/
//		String s = new String(header);
        try {
            mContentLength = parseContentLength(header);// ?
        } catch (NumberFormatException e) {
            return null;
        }
        /**
         * 根据帧数据的大小创建字节数组
         */
        byte[] frameData = new byte[mContentLength];
        readFully(frameData);
        /**
         * 根据不同的源(file，stream，byte-arrays)创建位图
         * 把输入字节流流转为位图
         */
        return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData));
    }
}
