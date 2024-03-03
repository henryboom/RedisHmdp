# 代码使用说明(本项目来自b站[黑马程序员](https://space.bilibili.com/37974444)[redis教程](https://www.bilibili.com/video/BV1cr4y1671t)，仅供参考)

项目代码包含2个分支：
- 前端资源在src/main/resources/nginx-1.18.0下

视频地址:
- [黑马程序员Redis入门到实战教程，深度透析redis底层原理+redis分布式锁+企业解决方案+redis实战](https://www.bilibili.com/video/BV1cr4y1671t)
- [https://www.bilibili.com/video/BV1cr4y1671t](https://www.bilibili.com/video/BV1cr4y1671t)
  - P24起 实战篇



## 常见问题
部分同学直接使用了master分支项目来启动，控制台会一直报错:
```
NOGROUP No such key 'stream.orders' or consumer group 'g1' in XREADGROUP with GROUP option
```
这是因为我们完整版代码会尝试访问Redis，连接Redis的Stream。建议同学切换到init分支来开发，如果一定要运行master分支，请先在Redis运行一下命令：
```text
XGROUP CREATE stream.orders g1 $ MKSTREAM
```
