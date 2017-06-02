# wechat
微信服务器
项目介绍：基于微信公众号开发的服务端项目，启用并设置服务器配置后，用户发给公众号的消息以及开发者需要的事件推送，将被微信转发到该URL中
项目框架：maven，spring mvc， mybatis，

一，配置maven的setting文件
选择Window-Preferences-Maven-User Settings,设置User Settings（选择配置好的settings文件），Local Reponsitory是根据settings文件里面的配置自动生成的。
默认为C:\Users\Administrator.PC_200901010042\.m2\repository
此处给出两个配置文件示例（settings.xml,settings2.xml）