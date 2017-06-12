# VideoSurveillanceSystem
远程视频监控

基于ARM的远程视频监控系统的实现与设计的PDF文件是本人在大学本科阶段的毕业论文。

文件夹下的AndroidApplication是Android的客户端。
/LinuxApplication/COMS摄像驱动  是OV7740摄像头对应的驱动程序
/LinuxApplication/USB摄像驱动   是OV7740摄像头USB接口对应的驱动程序
/LinuxApplication/mjpg-streamer  是对应的库，需要运行它所编译出来的文件就可以实现监控
/LinuxApplication/video2lcd    采集视频，将视频显示在LCD上
