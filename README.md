### 使用说明
推送RTSP，则需mediamtx支持；
https://github.com/bluenviron/mediamtx/releases/tag/v1.0.0

推送RTMP、HLS则需安装了nginx-http-flv-module的nginx支持
https://github.com/winshining/nginx-http-flv-module

## 示例
### 将rtsp推送为rtmp
VideoPushTool.pushVideo("rtsp://127.0.0.1:8554/test", "rtmp://127.0.0.1:1935/myapp/m1", "rtmp");

### 将输入流中的视频数据推送为rtmp
VideoPushTool.pushVideo(inputstream, "rtmp://127.0.0.1:1935/myapp/m1", "rtmp");

### 将byte[]推送到rtmp流
（调用时需要保证在同一线程，否则管道流将关闭）  
VideoPushTool.pushVideo(bytes, "rtmp://127.0.0.1:1935/myapp/m1", "rtmp");


## 支持

流媒体协议方面目前支持RTSP和RTMP；  
Nginx方面可将本服务的RTMP转为HLS推送。





## 依赖



       <dependencies>
            <dependency>
                <groupId>org.bytedeco</groupId>
                <artifactId>javacv-platform</artifactId>
                <version>1.5.</version>
            </dependency>
       <dependency>
            <groupId>org.bytedeco</groupId>
            <artifactId>ffmpeg-platform-gpl</artifactId>
            <version>6.0-1.5.9</version>
        </dependency>
    
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.3</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-to-slf4j</artifactId>
            <version>2.13.3</version>
        </dependency>


        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna</artifactId>
            <version>5.10.0</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.26</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>