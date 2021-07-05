package com.example.musicmix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by Zach on 2021/7/5 18:20
 */
public class MusicProcess {
    private static final String TAG = "MusicProcess";
    private static final int MAX_VOLUME = 32767;
    private static final int MIN_VOLUME = -32768;


    public void mixAudioTrack(Context context, String videoInput, String audioInput, String output,
                              int startTimeUs, int endTimeUs, int videoVolume, int audioVolume) {
        File videoPcmFile = new File(Environment.getExternalStorageDirectory(), "video.pcm");
        File musicPcmFile = new File(Environment.getExternalStorageDirectory(), "music.pcm");
        File mixPcmFile = new File(Environment.getExternalStorageDirectory(), "mix.pcm");

        decodeToPcm(videoInput, videoPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);

        mixPcm(videoPcmFile.getAbsolutePath(), musicPcmFile.getAbsolutePath(), mixPcmFile.getAbsolutePath(), videoVolume, audioVolume);
        new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_STEREO, 2, AudioFormat.ENCODING_PCM_16BIT)
                .pcmToWav(mixPcmFile.getAbsolutePath(), output);
    }



    public static void mixPcm(String pcm1Path, String pcm2Path, String toPath, int vol1, int vol2) {
        float volume1 = normalizeVolume(vol1);
        float volume2 = normalizeVolume(vol2);

        byte[] buffer1 = new byte[2048];
        byte[] buffer2 = new byte[2048];
        byte[] outputBuffer = new byte[2048];

        try {
            FileInputStream is1 = new FileInputStream(pcm1Path);
            FileInputStream is2 = new FileInputStream(pcm2Path);
            FileOutputStream fos = new FileOutputStream(toPath);

            short tmp1, tmp2;
            int sum;  // 波形叠加
            boolean end1 = false, end2 = false;
            while (!end1 || !end2) {
                if (!end1) {
                    end1 = (is1.read(buffer1) == -1);
                    System.arraycopy(buffer1, 0, outputBuffer, 0, buffer1.length);
                }

                if (!end2) {
                    end2 = (is2.read(buffer2) == -1);
                    for (int i = 0; i < buffer2.length; i += 2) {  // 声音2个字节
                        tmp1 = (short) ((buffer1[i] & 0xFF) | (buffer1[i + 1] & 0xFF << 8));
                        tmp2 = (short) ((buffer2[i] & 0xFF) | (buffer2[i + 1] & 0xFF << 8));
                        sum = (int) (tmp1 * volume1 + tmp2 * volume2);

                        if (sum > MAX_VOLUME) {
                            sum = MAX_VOLUME;
                        } else if (sum < MIN_VOLUME) {
                            sum = MIN_VOLUME;
                        }
                        outputBuffer[i] = (byte) (sum & 0xFF);
                        outputBuffer[i + 1] = (byte) ((sum >>> 8) & 0xFF);  //  >>> 无符号右移
                    }
                    fos.write(outputBuffer);
                }
            }
            is1.close();
            is2.close();
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @SuppressLint("WrongConstant")
    private void decodeToPcm(String inputPath, String outputPath, int startTime, int endTime) {
        if (endTime < startTime) {
            return;
        }

        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(inputPath);
            int audioTrack = selectTrack(mediaExtractor);
            mediaExtractor.selectTrack(audioTrack);
            mediaExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            MediaFormat oriAudioFormat = mediaExtractor.getTrackFormat(audioTrack);

            int maxBufferSize = 100 * 1000;
            if (oriAudioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxBufferSize = oriAudioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            }

            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            MediaCodec mediaCodec = MediaCodec.createDecoderByType(oriAudioFormat.getString((MediaFormat.KEY_MIME)));
            mediaCodec.configure(oriAudioFormat, null, null, 0);

            File pcmFile = new File(outputPath);
            FileChannel writeChannel = new FileOutputStream(pcmFile).getChannel();
            mediaCodec.start();

            // 处理解码内容
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outputBufferIndex = -1;
            while (true) {
                int dequeInputIndex = mediaCodec.dequeueInputBuffer(100_000);
                if (dequeInputIndex >= 0) {
                    long sampleTimeUs = mediaExtractor.getSampleTime();
                    if (sampleTimeUs == -1) {
                        break;
                    } else if(sampleTimeUs < startTime) {
                        mediaExtractor.advance();  // 丢掉不用
                        continue;
                    } else if (sampleTimeUs > endTime) {
                        break;
                    }

                    info.size = mediaExtractor.readSampleData(buffer, 0);
                    info.presentationTimeUs = sampleTimeUs;
                    info.flags = mediaExtractor.getSampleFlags();

                    byte[] content = new byte[buffer.remaining()];
                    buffer.get(content);

                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(dequeInputIndex);
                    inputBuffer.put(content);
                    mediaCodec.queueInputBuffer(dequeInputIndex, 0, info.size, info.presentationTimeUs, info.flags);
                    mediaExtractor.advance();   // 释放上一帧压缩数据
                }

                outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 100_000);
                while (outputBufferIndex >= 0) {
                    ByteBuffer decodeOutputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    writeChannel.write(decodeOutputBuffer);
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 100_000);
                }
            }
            writeChannel.close();
            mediaExtractor.release();
            mediaCodec.stop();
            mediaCodec.release();

            Log.i(TAG, "decodeToPcm complete");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int selectTrack(MediaExtractor mediaExtractor) {
        int numTracks = mediaExtractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }


    private static float normalizeVolume(int volume) {
        return volume / 100f;
    }
}
