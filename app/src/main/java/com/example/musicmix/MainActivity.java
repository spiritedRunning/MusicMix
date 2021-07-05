package com.example.musicmix;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;

        checkPermission();
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);

        }
        return false;
    }

    public void onMix(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String musicPath = new File(Environment.getExternalStorageDirectory(), "music.mp3").getAbsolutePath();
                String videoPath = new File(Environment.getExternalStorageDirectory(), "input.mp4").getAbsolutePath();
                String mixMusicPath = new File(Environment.getExternalStorageDirectory(), "output.mp3").getAbsolutePath();

                FileUtils.copyAssets(mContext,"music.mp3", musicPath);
                FileUtils.copyAssets(mContext, "input.mp4", videoPath);

                new MusicProcess().mixAudioTrack(mContext, videoPath, musicPath, mixMusicPath,
                        60 * 1000 * 1000, 70 * 1000 * 1000, 100, 15);
            }
        }).start();

    }
}