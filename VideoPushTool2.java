

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.javacv.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_DEBUG;

/**
 * 视频推送工具类
 * 使用前提：构建好需要推送协议的服务器，比如RTMP、RTSP服务
 */
@Slf4j
public class VideoPushTool {
    /**
     * 默认视频帧率
     */
    public static final int DEFAULT_VIDEO_FRAME_RATE = 30;
    public static Map<String, BufferedInputStream> inputStreamMap = new HashMap<>();
    public static Map<String, BufferedOutputStream> outputStreamMap = new HashMap<>();

    /**
     * 不断调用本方法将视频字节数据按照指定协议推送到指定地址
     * 调用本方法时必须保证始终在同一个线程，否则内部管道流关闭会导致出错。
     *
     * @param data        视频字节数据数据
     * @param pushAddress 推流地址（协议://地址）
     * @param protocol    协议，如 rtsp
     */
    public static void pushVideo(byte[] data, String pushAddress, String protocol) {
        try {
            synchronized (outputStreamMap) {
                BufferedInputStream inputStream = inputStreamMap.get(pushAddress);
                BufferedOutputStream outputStream = outputStreamMap.get(pushAddress);
                if (inputStream == null || outputStream == null) {
                    PipedInputStream pipedInputStream = new PipedInputStream();
                    inputStream = new BufferedInputStream(pipedInputStream);
                    outputStream = new BufferedOutputStream(new PipedOutputStream(pipedInputStream));
                    inputStreamMap.put(pushAddress, inputStream);
                    outputStreamMap.put(pushAddress, outputStream);

                    BufferedInputStream finalInputStream = inputStream;
                    Thread readerThread = new Thread(() -> {
                        try {
                            //开始视频推流
                            VideoPushTool.pushVideo(finalInputStream, pushAddress, protocol);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    readerThread.start();
                }
                outputStream.write(data);
            }
        } catch (IOException e) {
            log.info("清理管道流|{}", pushAddress);
            outputStreamMap.remove(pushAddress);
            inputStreamMap.remove(pushAddress);
            e.printStackTrace();
        }
    }

    /**
     * 按照指定协议将视频流数据推送到指定地址
     * 本方法只需调用一次。
     *
     * @param inputStream 获取视频数据的输入流
     * @param pushAddress 推流地址（协议://地址）
     * @param pushPotocol 协议，如 rtsp
     * @throws Exception
     */
    public static synchronized void pushVideo(InputStream inputStream, String pushAddress, String pushPotocol) throws Exception {
        FFmpegLogCallback.set();
        FFmpegLogCallback.setLevel(AV_LOG_DEBUG);
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputStream, 0);
        pushVideo4(pushAddress, pushPotocol, grabber);
    }

    /**
     * 推送视频流
     *
     * @param sourceAddress 源视频流地址（支持文件路径|网络流地址）
     * @param pushAddress   目标视频流地址（支持RTMP|RTSP）
     * @param pushPotocol   目标视频流协议（支持RTMP|RTSP）
     * @throws Exception
     */
    public static synchronized void pushVideo(String sourceAddress, String pushAddress, String pushPotocol) throws Exception {
        FFmpegLogCallback.set();
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(sourceAddress);
        pushVideo4(pushAddress, pushPotocol, grabber);
    }

    private static void pushVideo(String pushAddress, String pushPotocol, FFmpegFrameGrabber grabber) throws FrameRecorder.Exception, FrameGrabber.Exception {
        long startTime = System.currentTimeMillis();
        log.info("开始初始化帧抓取器");

        // 增加超时参数
        grabber.setOption("stimeout", "5000");
        //grabber.setVideoOption("threads", "1");

        // 初始化帧抓取器
        grabber.start();

        log.info("帧抓取器初始化完成，耗时[{}]毫秒", System.currentTimeMillis() - startTime);

        // grabber.start方法中，初始化的解码器信息存在放在grabber的成员变量oc中
        AVFormatContext avFormatContext = grabber.getFormatContext();
        // 数据源含有几个媒体流（一般是视频流+音频流）
        int streamNum = avFormatContext.nb_streams();
        if (streamNum < 1) {
            log.error("数据源中未找到媒体流！");
            return;
        }

        int frameRate = (int) grabber.getVideoFrameRate();
        if (0 == frameRate) {
            log.info("【捕获器】视频帧率为0，采用默认帧率：{}", DEFAULT_VIDEO_FRAME_RATE);
            frameRate = DEFAULT_VIDEO_FRAME_RATE;
        }
        log.info("【捕获器】视频帧率[{}]，视频时长[{}]秒，媒体流数量[{}]",
                frameRate,
                avFormatContext.duration() / 1000000,
                avFormatContext.nb_streams());

        for (int i = 0; i < streamNum; i++) {
            AVStream avStream = avFormatContext.streams(i);
            AVCodecParameters avCodecParameters = avStream.codecpar();
            log.info("【捕获器】流的索引[{}]，编码器类型[{}]，编码器ID[{}]", i, avCodecParameters.codec_type(), avCodecParameters.codec_id());
        }

        int frameWidth = grabber.getImageWidth();
        int frameHeight = grabber.getImageHeight();
        int audioChannels = grabber.getAudioChannels();
        log.info("【捕获器】格式 [{}] | 视频宽度 [{}] | 视频高度 [{}] | 视频编码 [id:{}、名称:{}]| 视频码率 [{}]| 视频帧率 [{}]",
                grabber.getFormat(),
                frameWidth,
                frameHeight,
                grabber.getVideoCodec(),
                grabber.getVideoCodecName(),
                grabber.getVideoBitrate(),
                grabber.getVideoFrameRate());
        log.info("【捕获器】 音频编码 [{}] | 音频码率 [{}] | 音频通道数 [{}]",
                grabber.getAudioCodecName(),
                grabber.getAudioBitrate(),
                audioChannels);

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(pushAddress,
                frameWidth,
                frameHeight,
                audioChannels);

        recorder.setInterleaved(true);

        switch (pushPotocol) {
            case "rtsp" -> {
                recorder.setFormat("rtsp");
                recorder.setVideoCodec(grabber.getVideoCodec());
            }
            case "rtmp" -> {
                recorder.setAudioCodec(AV_CODEC_ID_AAC);
                recorder.setVideoCodec(AV_CODEC_ID_H264);
                recorder.setFormat("flv");
            }
        }
        recorder.setFrameRate(frameRate);
        // 使用原始视频的码率
        recorder.setVideoBitrate(grabber.getVideoBitrate());

        //不能直接用grabber的音频码率，因为如果为0的话会报错
        //recorder.setAudioBitrate(grabber.getAudioBitrate());

        recorder.setGopSize(frameRate * 2);

        startTime = System.currentTimeMillis();
        log.info("开始初始化帧抓取器");

        avFormatContext.max_interleave_delta(0);
        avFormatContext.flags(avformat.AVFMT_TS_NONSTRICT);
        //recorder.setTimestamp(0);
        log.info("no timestamp");

        log.info("【录制器】格式 [{}] | 视频宽度 [{}] | 视频高度 [{}] | 视频编码 [{}]| 视频码率 [{}]",
                recorder.getFormat(),
                recorder.getImageWidth(),
                recorder.getImageHeight(),
                recorder.getVideoCodecName(),
                recorder.getVideoBitrate());
        log.info("【录制器】 | 音频编码 [{}] | 音频码率 [{}] | 音频通道数 [{}]",
                recorder.getAudioCodecName(),
                recorder.getAudioBitrate(),
                recorder.getAudioChannels());


        recorder.start();
        log.info("帧录制初始化完成，耗时[{}]毫秒", System.currentTimeMillis() - startTime);
        log.info("开始向推流（{}）", pushAddress);

        Frame frame;
        while ((frame = grabber.grabFrame()) != null) {
            // 将帧写入记录器
            recorder.record(frame);
            log.info("record");
        }

        log.info("推送完成，耗时[{}]秒。（{}）",
                (System.currentTimeMillis() - startTime) / 1000, pushAddress);
        // 关闭帧录制器
        // 关闭帧抓取器
        recorder.close();
        grabber.close();
        log.info("清理缓存管道流|{}", pushAddress);
        outputStreamMap.remove(pushAddress);
        inputStreamMap.remove(pushAddress);
    }
    private static void pushVideo4(String pushAddress, String pushPotocol, FFmpegFrameGrabber grabber) throws FrameRecorder.Exception, FrameGrabber.Exception {
        long startTime = System.currentTimeMillis();
        log.info("开始初始化帧抓取器");

        // 增加超时参数
        grabber.setOption("stimeout", "5000");
        grabber.setVideoOption("threads", "1");

        // 初始化帧抓取器
        grabber.start();

        log.info("帧抓取器初始化完成，耗时[{}]毫秒", System.currentTimeMillis() - startTime);

        // grabber.start方法中，初始化的解码器信息存在放在grabber的成员变量oc中
        AVFormatContext avFormatContext = grabber.getFormatContext();
        // 数据源含有几个媒体流（一般是视频流+音频流）
        int streamNum = avFormatContext.nb_streams();
        if (streamNum < 1) {
            log.error("数据源中未找到媒体流！");
            return;
        }

        int frameRate = (int) grabber.getVideoFrameRate();
        if (0 == frameRate) {
            log.info("【捕获器】视频帧率为0，采用默认帧率：{}", DEFAULT_VIDEO_FRAME_RATE);
            frameRate = DEFAULT_VIDEO_FRAME_RATE;
        }
        log.info("【捕获器】视频帧率[{}]，视频时长[{}]秒，媒体流数量[{}]",
                frameRate,
                avFormatContext.duration() / 1000000,
                avFormatContext.nb_streams());

        for (int i = 0; i < streamNum; i++) {
            AVStream avStream = avFormatContext.streams(i);
            AVCodecParameters avCodecParameters = avStream.codecpar();
            log.info("【捕获器】流的索引[{}]，编码器类型[{}]，编码器ID[{}]", i, avCodecParameters.codec_type(), avCodecParameters.codec_id());
        }

        int frameWidth = grabber.getImageWidth();
        int frameHeight = grabber.getImageHeight();
        int audioChannels = grabber.getAudioChannels();
        String grabberVideoCodecName = grabber.getVideoCodecName();
        log.info("【捕获器】格式 [{}] | 视频宽度 [{}] | 视频高度 [{}] | 视频编码 [id:{}、名称:{}]| 视频码率 [{}]| 视频帧率 [{}]",
                grabber.getFormat(),
                frameWidth,
                frameHeight,
                grabber.getVideoCodec(),
                grabberVideoCodecName,
                grabber.getVideoBitrate(),
                grabber.getVideoFrameRate());
        log.info("【捕获器】 音频编码 [{}] | 音频码率 [{}] | 音频通道数 [{}]",
                grabber.getAudioCodecName(),
                grabber.getAudioBitrate(),
                audioChannels);

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(pushAddress,
                frameWidth,
                frameHeight,
                audioChannels);

        recorder.setInterleaved(true);
        recorder.setSampleRate(grabber.getSampleRate());
        recorder.setVideoCodec(grabber.getVideoCodec());

        switch (pushPotocol) {
            case "rtsp" -> {
                recorder.setFormat("rtsp");
            }
            case "rtmp" -> {
                //flv只支持h264
                if (!"h264".equals(grabberVideoCodecName)) {
                    recorder.setVideoCodec(AV_CODEC_ID_H264);
                }
                recorder.setAudioCodec(grabber.getAudioCodec());
                recorder.setFormat("flv");
            }
        }
        recorder.setFrameRate(frameRate);
        // 使用原始视频的码率
        recorder.setVideoBitrate(grabber.getVideoBitrate());

        //不能直接用grabber的音频码率，因为如果为0的话会报错
        //recorder.setAudioBitrate(grabber.getAudioBitrate());

        //Group of Pictures，越小延迟越小，不过画质可能会降低，越大延迟越大，画质可能提升。
        recorder.setGopSize(3);

        startTime = System.currentTimeMillis();
        log.info("开始初始化帧抓取器");

        avFormatContext.max_interleave_delta(0);
        avFormatContext.flags(avformat.AVFMT_TS_NONSTRICT);
        //recorder.setTimestamp(0);
        log.info("no timestamp");

        log.info("【录制器】格式 [{}] | 视频宽度 [{}] | 视频高度 [{}] | 视频编码 [{}]| 视频码率 [{}]",
                recorder.getFormat(),
                recorder.getImageWidth(),
                recorder.getImageHeight(),
                recorder.getVideoCodecName(),
                recorder.getVideoBitrate());
        log.info("【录制器】 | 音频编码 [{}] | 音频码率 [{}] | 音频通道数 [{}]",
                recorder.getAudioCodecName(),
                recorder.getAudioBitrate(),
                recorder.getAudioChannels());


        recorder.start();
        log.info("帧录制初始化完成，耗时[{}]毫秒", System.currentTimeMillis() - startTime);
        log.info("开始向推流（{}）", pushAddress);

        Frame frame;
        int countFrames = 0;
        long time1 = System.currentTimeMillis();
        long countCosumedMs = 0;
        long totalCosumedMs = 0;
        while ((frame = grabber.grabFrame()) != null) {
            // 将帧写入记录器
            recorder.record(frame);
            if (isVideoFrame(frame)) {
                countFrames++;
            }
            if (countFrames == frameRate) {
                countCosumedMs++;
                long consumedMs = System.currentTimeMillis() - time1;
                totalCosumedMs += consumedMs;
                log.info("记录{}帧（视频），耗时:{} ms，第{}秒,平均耗时{}ms", countFrames, consumedMs, countCosumedMs, totalCosumedMs / countCosumedMs);
                time1 = System.currentTimeMillis();
                countFrames = 0;
            }
        }

        log.info("推送完成，耗时[{}]秒。（{}）",
                (System.currentTimeMillis() - startTime) / 1000, pushAddress);
        // 关闭帧录制器
        // 关闭帧抓取器
        recorder.close();
        grabber.close();
        log.info("清理缓存管道流|{}", pushAddress);
        outputStreamMap.remove(pushAddress);
        inputStreamMap.remove(pushAddress);
    }

    public static boolean isVideoFrame(Frame frame) {
        int channels = frame.imageChannels;
        int sampleRate = frame.sampleRate;

        return channels > 0 && sampleRate == 0;
    }

}