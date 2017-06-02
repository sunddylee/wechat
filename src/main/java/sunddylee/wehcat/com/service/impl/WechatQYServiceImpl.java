/*

import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


*//**
 * 核心服务类
 *
 * @author manson
 * @date 2013-05-20
 *//*
@Service(value = "wechatQYServiceImpl")
public class WechatQYServiceImpl implements WechatQYService {

    public static final Logger logger = Logger.getLogger(WechatQYServiceImpl.class);

    @Autowired
    WeixinQrCodeScanStatisticsService weixinQrCodeScanStatisticsService;
    @Autowired
    RMIWechatService rmiWechatService;

    public String processRequest(HttpServletRequest request) {
        String respMessage = null;
        // 默认返回的文本消息内容
        String respContent = "请求处理异常，请稍候尝试！";
        try {

            // xml请求解析
            Map<String, String> requestMap = MessageUtil.parseXml(request);
            String fromUserName = requestMap.get("FromUserName");// 发送方帐号（open_id）
            String toUserName = requestMap.get("ToUserName"); // 公众帐号
            String content = requestMap.get("Content"); // 接收内容
            String msgType = requestMap.get("MsgType"); // 消息类型
            String eventType = requestMap.get("Event");// 事件类型

            // 回复文本消息
            TextMessageResponse textMessage = new TextMessageResponse();
            textMessage.setToUserName(fromUserName);
            textMessage.setFromUserName(toUserName);
            textMessage.setCreateTime(new Date().getTime());
            textMessage.setMsgType(MessageUtil.RESP_MESSAGE_TYPE_TEXT);
            textMessage.setFuncFlag(0);

            // 文本消息
            if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_TEXT)) {
                respContent = getMsg(content, request, textMessage);
            }
            // 图片消息
            else if (msgType.equals(MessageUtil.REQ_MESSAGE_TYPE_IMAGE)) {
                respContent = "您发送的是图片消息！";
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

                if (eventType.equals(MessageUtil.EVENT_TYPE_SUBSCRIBE)) {  // 订阅
                    logger.info("EVENT_TYPE_SUBSCRIBE");
                } else if (eventType.equals(MessageUtil.RESP_MESSAGE_TYPE_SCAN)) { // 扫描
                    respContent = getMsg(content, request, textMessage);
                } else if (eventType.equals(MessageUtil.EVENT_TYPE_UNSUBSCRIBE)) {   // 取消订阅
                    logger.info("EVENT_TYPE_UNSUBSCRIBE");
                } else if (eventType.equals(MessageUtil.EVENT_TYPE_CLICK)) {// 自定义菜单点击事件
                    // 事件KEY值，与创建自定义菜单时指定的KEY值对应
                    String eventKey = requestMap.get("EventKey");
                    requestMap.put("Content", eventKey);
                    content = eventKey;
                    logger.info("eventKey: " + eventKey);
                    respContent = getMsg(content, request, textMessage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Exception while processRequest: ", e);
        }

        return respContent;
    }

    // 根据消息绑定信息
    public String getMsg(String msg_content, HttpServletRequest res, TextMessageResponse textMessage) throws Exception {
        StringBuffer buffer = new StringBuffer();
        buffer.append(defaultMsg());
        textMessage.setContent(buffer.toString());
        return MessageUtil.textMessageToXml(textMessage);
    }

    public String defaultMsg() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("您好！欢迎关注【悟空找房】！\n");
        buffer.append("想买房，银行不帮忙，老婆不帮忙，谁帮忙？\n");
        buffer.append("银行不贷悟空贷！老婆不贷悟空贷！\n");
        buffer.append("悟空首付贷，贷你成家！\n");
        buffer.append("悟空闪付贷，贷你回家！\n");
        buffer.append("悟空赎楼贷，带你找新家！\n");
        buffer.append("想买房，认准悟空找房！\n");
        buffer.append("直接回复“悟空贷”了解更多！\n").append("详情请咨询：4008215365。\n");
        return buffer.toString();
    }


    public PullResponse pullMessageActOrder(PushMsgRequest pushMsgRequest) throws Exception {
        com.lifang.model.Response<String> result = rmiWechatService.pushMessage(pushMsgRequest, WechatQyAppEnum.APP_ACT_ORDER);
        return FasterJsonTool.readValue(FasterJsonTool.writeValueAsString(result.getData()), PullResponse.class);
    }

}
*/