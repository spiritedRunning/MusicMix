package com.example.musicmix;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Environment;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static void writeBytes(byte[] array, String fileName) {
        FileOutputStream writer = null;
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            writer = new FileOutputStream(Environment.getExternalStorageDirectory() + "/" + fileName, true);
            writer.write(array);
            writer.write('\n');


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String writeContent(byte[] array, String fileName) {
        char[] HEX_CHAR_TABLE = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(HEX_CHAR_TABLE[(b & 0xf0) >> 4]);
            sb.append(HEX_CHAR_TABLE[b & 0x0f]);
        }
        Log.i(TAG, "writeContent: " + sb.toString());
        FileWriter writer = null;
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            writer = new FileWriter(Environment.getExternalStorageDirectory() + "/" + fileName, true);
            writer.write(sb.toString());
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    public static void copyAssets(Context context, String assetsName, String path) {
        AssetFileDescriptor assetFileDescriptor = null;
        try {
            assetFileDescriptor = context.getAssets().openFd(assetsName);
            FileChannel from = new FileInputStream(assetFileDescriptor.getFileDescriptor()).getChannel();
            FileChannel to = new FileOutputStream(path).getChannel();
            from.transferTo(assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength(), to);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
