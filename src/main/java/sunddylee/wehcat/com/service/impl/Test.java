/*package com.lifang.wechatSvr.service.wechat.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lifang.imgsoa.model.ImageKeyObject;
import com.lifang.json.FasterJsonTool;
import com.lifang.model.Response;
import com.lifang.wechatSvr.common.BusinessException;
import com.lifang.wechatSvr.common.Global;
import com.lifang.wechatSvr.entity.WeixinQrCodeStatistics;
import com.lifang.wechatSvr.message.req.BaseMessageRequest;
import com.lifang.wechatSvr.message.req.ImageMessageRequest;
import com.lifang.wechatSvr.message.req.TextMessageRequest;
import com.lifang.wechatSvr.message.resp.Article;
import com.lifang.wechatSvr.message.resp.TextMessageResponse;
import com.lifang.wechatSvr.service.ActivityDataService;
import com.lifang.wechatSvr.service.BaseService;
import com.lifang.wechatSvr.service.ImageSOAService;
import com.lifang.wechatSvr.service.RedpackService;
import com.lifang.wechatSvr.service.UserService;
import com.lifang.wechatSvr.service.WeixinMsgService;
import com.lifang.wechatSvr.service.WeixinQrCodeScanStatisticsService;
import com.lifang.wechatSvr.service.wechat.WechatService;
import com.lifang.wechatSvr.util.ResponseUtil;
import com.lifang.wechatSvr.util.StringUtil;
import com.lifang.wechatSvr.util.wechat.MessageUtil;
import com.lifang.wechatSvr.util.wechat.WechatUtil;
import com.lifang.wechatsoa.common.CommonConst;
import com.lifang.wechatsoa.facade.RMIWechatService;
import com.lifang.wechatsoa.model.JSSignResponse;
import com.lifang.wechatsoa.model.Oauth2AccessRequest;
import com.lifang.wechatsoa.model.Oauth2WeixinUserRequest;

*//**
 * 核心服务类
 *
 * @author manson
 * @date 2013-05-20
 *//*
@Service(value = "weixinServiceImpl")
public class WechatServiceImpl extends BaseService implements WechatService {

    public static final Logger logger = Logger.getLogger(WechatServiceImpl.class);

    public static final int CACHE_TICKETS_TIME = (int) (60 * 60 * 0.5); // 过期时间为，一个半小时.

    public static int wechatCode = CommonConst.WECHAT_SERVICE_WKZF;

    @Autowired
    UserService userService;
    @Autowired
    ActivityDataService activityDataService;
    @Autowired
    RedpackService redpackService;
    @Autowired
    WeixinQrCodeScanStatisticsService weixinQrCodeScanStatisticsService;
    @Autowired
    WeixinMsgService weixinMsgService;

    @Autowired
    ImageSOAService imageSOAService;

    @Autowired
    RMIWechatService rmiWechatService;

    private static int wechatkeyCode = CommonConst.WECHAT_SERVICE_WKZF; //服务号: 悟空找房

    *//**
     * 处理微信发来的请求
     *
     * @param request
     * @return
     *//*
    public String processRequest(HttpServletRequest request) {
        // 默认返回的文本消息内容
        String respContent = "请求处理异常，请稍候尝试！";
        try {

            // xml请求解析
            Map<String, String> requestMap = MessageUtil.parseXml(request);
            BaseMessageRequest baseMessageRequest = fetchBaseMessageRequest(requestMap); // 获取微信的基本信息
            String fromUserName = baseMessageRequest.getFromUserName();                  // 发送方帐号（open_id）
            String toUserName = baseMessageRequest.getToUserName();                      // 开发者微信号
            String msgType = baseMessageRequest.getMsgType();                            // 消息类型（text/image/location/link）
            String content = requestMap.get("Content");                                  // 消息内容
            String eventType = requestMap.get("Event");                                  // 事件类型
            String eventKey2 = requestMap.get("EventKey");                               // 事件的值
            logger.info("processRequest requestMap:" + requestMap);

            // 回复文本消息
            TextMessageResponse textMessage = new TextMessageResponse();
            textMessage.setToUserName(fromUserName);
            textMessage.setFromUserName(toUserName);
            textMessage.setCreateTime(new Date().getTime());
            textMessage.setMsgType(MessageUtil.RESP_MESSAGE_TYPE_TEXT);
            textMessage.setFuncFlag(0);

            // 文本消息
            if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_TEXT)) {

                ResponseUtil backResponse = addWeixinTextMsg(requestMap);
                if (null != backResponse && 1 == backResponse.getStatus()) {
                    respContent = "";
                }
            }
            // 图片消息
            else if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_IMAGE)) {
                ResponseUtil backResponse = addWeixinImageMsg(requestMap);
                if (null != backResponse && 1 == backResponse.getStatus()) {
                    respContent = "";
                }
            }
            // 地理位置消息
            else if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_LOCATION)) {
                respContent = "您发送的是地理位置消息！";
            }
            // 链接消息
            else if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_LINK)) {
                respContent = "您发送的是链接消息！";
            }
            // 音频消息
            else if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_VOICE)) {
                respContent = "您发送的是音频消息！";
            }
            // 事件推送
            else if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_EVENT)) {

                logger.info("事件推送");
                WeixinQrCodeStatistics qrScan = new WeixinQrCodeStatistics();
                Date date = new Date();
                qrScan.setDate(date);

                // 订阅
                if (eventType.equals(MessageUtil.EVENT_TYPE_SUBSCRIBE)) {
                    qrScan.setEventType(1);

                    String sceneId = "";
                    if (!StringUtil.isNullOrEmpty(eventKey2)) {
                        String[] arr = eventKey2.split("_");
                        logger.info("用户扫描关注微信公共号");

                        if (arr.length >= 2) {
                            sceneId = arr[1];
                            qrScan.setChannelCode(Integer.parseInt(sceneId));
                            weixinQrCodeScanStatisticsService.handleWeiXinQrCodeScan(qrScan);
                            respContent = processMsg(content, request, textMessage);
                        }
                    }
                    // 扫描
                } else if (eventType.equals(MessageUtil.RESP_MESSAGE_TYPE_SCAN)) {
                    respContent = processMsg(content, request, textMessage);
                }

                // 取消订阅
                else if (eventType.equals(MessageUtil.EVENT_TYPE_UNSUBSCRIBE)) {
                    // TODO 取消订阅后用户再收不到公众号发送的消息，因此不需要回复消息
                    // 取消账号绑定
                    // userInfoService.unBindAccount(fromUserName);
                }
                // 自定义菜单点击事件
                else if (eventType.equals(MessageUtil.EVENT_TYPE_CLICK)) {
                    // 事件KEY值，与创建自定义菜单时指定的KEY值对应
                    String eventKey = requestMap.get("EventKey");
                    requestMap.put("Content", eventKey);
                    content = eventKey;
                    logger.info("eventKey: " + eventKey);
                    //一抢头速销
                    if (content.equals("V1002_4")) {
                        List<Article> articleList = new ArrayList<Article>();
                        Article article = new Article();
                        article.setTitle("重磅！悟空找房推出【一抢头】速销业务，让您的房子卖得更快！更好！");
                        article.setDescription("重磅！悟空找房推出【一抢头】速销业务，让您的房子卖得更快！更好！");
                        article.setPicUrl("http://mmbiz.qpic.cn/mmbiz/PIoZlIrn8NqklUXzXEzwH3ib7ukBGDnn5Q4vHDydJArrxokupiaoqdpAYWPyPmxZ4VFW5e6arIZZHXeSaq16sPdg/640?wx_fmt=jpeg&tp=webp&wxfrom=5&wx_lazy=1");
                        article.setUrl("http://mp.weixin.qq.com/s?__biz=MzA3Nzk0ODA4OA==&mid=208034984&idx=1&sn=d7021e4eeb4c10d965675b071f2060a4&scene=18#rd");
                        articleList.add(article);
                        respContent = MessageUtil.ResNewsMessage(fromUserName, toUserName, articleList);
                    } else {
                        respContent = processMsg(content, request, textMessage);
                    }

                }

                logger.info("this weixin scan info is:MsgType=" + msgType + ",eventType=" + eventType + ",ToUserName=" + toUserName
                        + ",fromUserName=" + fromUserName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Exception while processRequest: ", e);
        }

        return respContent;
    }

    // 根据消息绑定信息
    public String processMsg(String msg_content, HttpServletRequest res, TextMessageResponse textMessage) throws Exception {
        StringBuffer buffer = new StringBuffer();

        // 处理逻辑:"悟空找房超级棒(发放红包)"
//        if (StringUtil.equal("李壮测红包", msg_content)) {
//            return redpackService.signUpWeixinUserByRedpack(msg_content, res, textMessage);
//        }

        
         * int activity_id = WechatUtil.Now_Activity; //处理逻辑："最新活动" if
         * (StringUtil.equal("0,最新活动", msg_content)) {
         * buffer.append("大家好,利房网已经推出最新活动啦.").append("\n"); String url =
         * "http://weixin.lifang.com/html5_project/activity/index2.html";
         * buffer.append("请点击<a href='" + url + "?weixin_openid=" +
         * textMessage.getToUserName() + "'>活动详情</a>.").append("\n");
         * textMessage.setContent(buffer.toString()); return
         * PullMessageUtil.textMessageToXml(textMessage); }
         * 
         * //处理逻辑："wybm,我要报名" if (StringUtil.equal("1,wybm,我要报名", msg_content)){
         * return userService.signUpToWeixin(msg_content, res, textMessage,
         * activity_id); } //处理逻辑: "查询分享次数" else if
         * (StringUtil.equal("2,fxcs,查询分享次数", msg_content)){ return
         * activityDataService.getActivityDataCountToWexin(msg_content, res,
         * textMessage, activity_id); } //处理逻辑： "获取邀请码" else if
         * (StringUtil.equal("3,xhewm,获取邀请码", msg_content)) { return
         * qrcodeService.getQrcodeToWeixin(msg_content, res, textMessage,
         * activity_id); } //处理逻辑："公司+姓名+手机号码" else if
         * (msg_content.split("[+]").length == 3){ // Pattern reg =
         * Pattern.compile("+"); // Matcher m = reg.matcher("msg_content"); //
         * m.groupCount() int result =
         * userService.updateUserToWeixin(msg_content, res, textMessage,
         * activity_id);
         * 
         * //StringBuffer bufferReg = new StringBuffer(); if(result == 0){
         * return qrcodeService.getQrcodeToWeixin(msg_content, res, textMessage,
         * activity_id);
         * 
         * }else if(result == -1){
         * buffer.append("您的分享次数没有达到标准，无法更新人员资料").append("\n");
         * buffer.append("如果您想查看分享次数，请发送“我要查询”可查看分享次数。").append("\n");
         * 
         * textMessage.setContent(buffer.toString()); return
         * PullMessageUtil.textMessageToXml(textMessage); }else if(result == -2){
         * buffer.append("您输入的格式不对,格式为: 公司+员工+电话号码。").append("\n");
         * textMessage.setContent(buffer.toString());
         * 
         * return PullMessageUtil.textMessageToXml(textMessage); }else if(result ==
         * -3){ buffer.append("您输入的电话号码格式有问题。").append("\n");
         * textMessage.setContent(buffer.toString()); return
         * PullMessageUtil.textMessageToXml(textMessage); }else if(result == -4){
         * buffer.append("抱歉，更新失败，请确认您是否已报名。");
         * textMessage.setContent(buffer.toString()); return
         * PullMessageUtil.textMessageToXml(textMessage); } }
         

        
         * //处理逻辑： "关于我们" if (StringUtil.equal("33", msg_content)) { String url
         * = "http://10.0.18.123/wap/lifang/page/detail.php";
         * buffer.append("请点击<a href='" + url + "'>测试页面</a>.").append("\n");
         * textMessage.setContent(buffer.toString()); return
         * PullMessageUtil.textMessageToXml(textMessage); }
         * 
         * //处理逻辑： "关于我们" if (StringUtil.equal("00,关于我们", msg_content)) {
         * buffer.append("我们是利房网的微信开发团队.").append("\n");
         * buffer.append("期待大家的关注.").append("\n");
         * textMessage.setContent(buffer.toString()); return
         * PullMessageUtil.textMessageToXml(textMessage); }
         * 
         * if (msg_content.equals("")){ buffer.append("您好,我是小利.").append("\n");
         * buffer.append("很高兴为您服务").append("\n"); }else{
         * buffer.append("对不起无法识别您的信息").append("\n"); }
         
        //buffer.append(defaultMsg());
        textMessage.setContent(""); //默认为空,不会发送任何信息.
        return MessageUtil.textMessageToXml(textMessage);
        //return "";
    }

    *//**
     * 功能描述:TODO(默认回复信息)
     *
     * @return
     * @author
     *//*
    public String defaultMsg() {
        StringBuilder buffer = new StringBuilder();

        buffer.append("您好！欢迎关注【悟空找房】！\n");
        buffer.append("想买房，银行不帮忙，老婆不帮忙，谁帮忙？\n");
        buffer.append("银行不贷悟空贷！老婆不贷悟空贷！\n");
        buffer.append("悟空首付贷，贷你成家！\n");
        buffer.append("悟空闪付贷，贷你回家！\n");
        buffer.append("悟空赎楼贷，带你找新家！\n");
        buffer.append("想买房，认准悟空找房！\n");
        buffer.append("直接回复“悟空贷”了解更多！\n");
        buffer.append("详情请咨询：4008215365。\n");
        return buffer.toString();
    }

    public JSSignResponse getJsSign(String requestUrl) throws Exception {
        String jsrJson = FasterJsonTool.writeValueAsString(rmiWechatService.getJsSDKConfigInfo(wechatCode, requestUrl).getData());
        return FasterJsonTool.readValue(jsrJson, JSSignResponse.class);
    }

    private List<Article> getArticleList(String newId, String picUrl) {
        List<Article> articleList = new ArrayList<Article>();
        if (newId.equals("123")) {
            Article article = new Article();
            article.setTitle("悟空找房11.11大闹新房 请你吃山核桃啦！");
            article.setDescription("点击抽奖赢取山核桃一罐");
            article.setPicUrl(picUrl);
            article.setUrl(Global.S_WECHAT_SHARE_URL);
            articleList.add(article);
        }
        return articleList;
    }

    *//**
     * 新增微信-消息
     *
     * @return
     *//*
    public ResponseUtil addWeixinTextMsg(Map<String, String> requestMap) {

        BaseMessageRequest baseMessageRequest = fetchBaseMessageRequest(requestMap);
        String content = requestMap.get("Content");// 消息内容
        TextMessageRequest textMessageRequest = new TextMessageRequest();
        BeanUtils.copyProperties(baseMessageRequest, textMessageRequest);
        textMessageRequest.setContent(content);

        return weixinMsgService.addWeixinTextMsg(textMessageRequest);
    }

    *//**
     * 新增微信-图片消息
     *
     * @return
     *//*
    public ResponseUtil addWeixinImageMsg(Map<String, String> requestMap) throws Exception {
        BaseMessageRequest baseMessageRequest = fetchBaseMessageRequest(requestMap);
        String picUrl = requestMap.get("PicUrl");// 消息内容

        ImageMessageRequest imageMessageRequest = new ImageMessageRequest();
        BeanUtils.copyProperties(baseMessageRequest, imageMessageRequest);
        String mediaId = requestMap.get("MediaId");
        imageMessageRequest.setMediaId(mediaId);
        imageMessageRequest.setPicUrl(picUrl);
        //从微信服务器 下载图片
        byte[] image = WechatUtil.mediaFetch(rmiWechatService.getAccessToken().getData(), mediaId);

        //上传图片进公司阿里云
        String imageKey = imageSOAService.uploadImage(image);

        imageMessageRequest.setImageKey(imageKey);

        return weixinMsgService.addWeixinImageMsg(imageMessageRequest);
    }

    public ImageKeyObject mediaFetch(String mediaId) throws Exception {
        //从微信服务器 下载图片
        byte[] image = WechatUtil.mediaFetch(rmiWechatService.getAccessToken().getData(), mediaId);
        //上传图片进公司阿里云
        return imageSOAService.uploadSingleFile(image);
    }

    *//**
     * 获取微信的基本信息
     *
     * @param requestMap
     * @return
     *//*
    public BaseMessageRequest fetchBaseMessageRequest(Map<String, String> requestMap) {
        BaseMessageRequest baseMessageRequest = new BaseMessageRequest();
        try {
            String fromUserName = requestMap.get("FromUserName");// 发送方帐号（open_id）
            String toUserName = requestMap.get("ToUserName"); // 公众帐号
            String msgType = requestMap.get("MsgType"); // 消息类型
            if (null != requestMap.get("AgentID")) {
                Long agentID = Long.valueOf(requestMap.get("AgentID"));// 事件类型
                baseMessageRequest.setAgentID(agentID);
            }
            if (null != requestMap.get("MsgId")) {
                Long msgId = Long.valueOf(requestMap.get("MsgId"));// msgId
                baseMessageRequest.setMsgId(msgId);
            }

            baseMessageRequest.setFromUserName(fromUserName);
            baseMessageRequest.setToUserName(toUserName);
            baseMessageRequest.setMsgType(msgType);

        } catch (Exception ex) {
            logger.info("BaseMessageRequest error" + ex.getMessage());
        }
        return baseMessageRequest;

    }

    public Oauth2WeixinUserRequest getOauth2WeiXinUser(Oauth2AccessRequest oauth2AccessRequest) throws Exception {
        Response<String> oauth2WeixinUserRequestResponse = rmiWechatService.getOauth2WeiXinUser(wechatkeyCode, oauth2AccessRequest);
        String strJson = FasterJsonTool.writeValueAsString(oauth2WeixinUserRequestResponse.getData());
        logger.info("getOauth2WeiXinUser strJson: " + strJson);
        return FasterJsonTool.readValue(strJson, Oauth2WeixinUserRequest.class);
    }

    public Oauth2AccessRequest getOauth2AccessToken(String code) throws Exception {
        Response<String> oauth2AccessTokenResponse = rmiWechatService.getOauth2AccessToken(wechatCode, code);
        String jsonData = FasterJsonTool.writeValueAsString(oauth2AccessTokenResponse.getData());
        logger.info("oauth2AccessTokenResponse:" + jsonData);
        return FasterJsonTool.readValue(jsonData, Oauth2AccessRequest.class);
    }

    public String getOauth2Code(String requestUrl) throws Exception {
        Response<String> oauth2Code = rmiWechatService.getOauth2Code(wechatCode, requestUrl, "snsapi_base");//调用weixinSOA授权访问 不弹出授权框模式
        logger.info(" request url :" + oauth2Code.getData());
        return oauth2Code.getData();
    }

    @Override
    public JSSignResponse getJsSign(String requestUrl, Integer wechatCode) throws Exception {
        if(null == wechatCode) {
            wechatCode = CommonConst.WECHAT_SERVICE_WKZF;
        }else{
            if(wechatCode != CommonConst.WECHAT_SUBSCRIBE &&
                wechatCode != CommonConst.WECHAT_SERVICE_WKZF &&
                wechatCode != CommonConst.WECHAT_SERVICE_DSX &&
                wechatCode != CommonConst.WECHAT_ENTERPRISE &&
                wechatCode != CommonConst.WECHAT_SERVICE_WKZF_APP &&
                wechatCode != CommonConst.WECHAT_QY_AGENT_TYPE_MONITOR){
                throw new BusinessException(0,"参数不合法");
            }
        }
        
        String jsrJson = FasterJsonTool.writeValueAsString(rmiWechatService.getJsSDKConfigInfo(wechatCode, requestUrl).getData());
        return FasterJsonTool.readValue(jsrJson, JSSignResponse.class);
        
    }
}
*/