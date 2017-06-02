/*

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import redis.clients.jedis.JedisCluster;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

@Service
public class RMIWechatServiceImpl extends AbstractRMIServiceBase implements RMIWechatService {
    private static Logger logger = LoggerFactory.getLogger(RMIWechatServiceImpl.class);
    //服务号与订阅号
    private static String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=APP_ID&secret=APP_SECRET";
    private static String WEIXIN_USER_URL = "https://api.weixin.qq.com/cgi-bin/user/info?access_token=ACCESS_TOKEN&openid=OPENID&lang=zh_CN";
    private static String SEND_TEMPLATE_MESSAGE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=ACCESS_TOKEN";
    private static String QRCODE_CREATE_URL = "https://api.weixin.qq.com/cgi-bin/qrcode/create?access_token=ACCESS_TOKEN";
    private static String QRCODE_SHOW_URL = "https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=TICKET";
    private static String GET_TICKET_URL = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=ACCESS_TOKEN&type=jsapi";
    private static String MESSAGE_CUSTOM_SEND = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=ACCESS_TOKEN";
    private static String GET_LASTER_MATERIAL = "https://api.weixin.qq.com/cgi-bin/material/batchget_material?access_token=ACCESS_TOKEN";

    //网页授权
    private static String OAUTH2_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=APP_ID&secret=APP_SECRET&code=CODE&grant_type=authorization_code";
    private static String OAUTH2_WEIXIN_USER_URL = "https://api.weixin.qq.com/sns/userinfo?access_token=ACCESS_TOKEN&openid=OPENID&lang=zh_CN";
    private static String OAUTH2_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=APPID&redirect_uri=REDIRECT_URI&response_type=code&scope=SCOPE&state=STATE#wechat_redirect";

    //企业号
    private static String ACCESS_TOKEN_ENTERPRISE_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=APP_ID&corpsecret=APP_SECRET";
    private static String PULL_MESSAGE_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=ACCESS_TOKEN";

    @Autowired
    @Qualifier("jedisCluster")
    private JedisCluster jedisCluster;
    
    public static final String ACCESS_TOKEN = "_accessToken";
    public static final String JS_APITICKET = "_JsApiTicket";
    public static final int EXPRIE_TIME = 60*20; //过期时间为，20分钟.
    @Autowired
    private WeixinQyAgentReadMapper weixinQyAgentReadMapper;

    @Autowired
    private FileService rmiFileService;

    @Value("${S_evn}")
    private String evn;

    @Override
    public Response<String> getAccessToken() throws Exception {
        //默认为服务号: 悟空找房
        return getAccessToken(CommonConst.WECHAT_SERVICE_WKZF);
    }

    @Override
    public Response<String> getAccessToken(Integer wechatkeyCode) throws Exception {
        return getAccessToken(wechatkeyCode, false);
    }

    @Override
    public Response<String> resetAccessToken(Integer wechatkeyCode) throws Exception {
        return getAccessToken(wechatkeyCode, true);
    }

    public Response<String> getAccessToken(Integer wechatkeyCode, boolean isReset) throws Exception {
        if (null == wechatkeyCode || 0 == wechatkeyCode) {
            return responseWrite("0", "wechatkeyCode 不能为空", null);
        }

        WeixinConfigResponse weixinConfigModel = getWeixinConfigModel(wechatkeyCode);
        if (null == weixinConfigModel) {
            return responseWrite("0", "没有找到对应的公共号配置信息,请重新正确的wechatkeyCode", null);
        }

        String getAccessTokenKeys = KeyWkbird.weiXinQYAccessToken.key(ACCESS_TOKEN) + "_" + weixinConfigModel.getAppId();
        Object accessToken = jedisCluster.get(getAccessTokenKeys);
        logger.info("RMIWechatServiceImpl key accesstoken: getAccessTokenKeys {} accessToken{}", getAccessTokenKeys, accessToken);
        if (null != accessToken && isReset == false) {
            logger.info("redis accesToken:" + accessToken);
            return responseWrite(null, accessToken);
        }

        String jsonStr = HttpUtil.httpsRequest(weixinConfigModel.getRequestURl(), "GET", null);

        AccessTokenModel accessTokenModel = JSON.parseObject(jsonStr, AccessTokenModel.class);
        // 如果请求错误,返回提示
        if (null == accessTokenModel.getAccess_token()) {
            return responseWrite("0", "获取accesstoken 失败.", jsonStr);
        }

        accessToken = accessTokenModel.getAccess_token();
        logger.info("通过微信官方api 获取 accesstoken:" + weixinConfigModel.getAppId() + ":" + accessToken);
        jedisCluster.set(getAccessTokenKeys, accessToken.toString());
        jedisCluster.expire(getAccessTokenKeys, EXPRIE_TIME); //30分钟后失效
        return responseWrite(null, accessToken);
    }

    public Response<String> pushMessage(PushMsgRequest pullMsgRequest) throws Exception {
        return pushMessage(pullMsgRequest, null);
    }

    @Override
    public Response<String> pushMessage(PushMsgRequest pullMsgRequest, WechatQyAppEnum appEnum) throws Exception {
        if (null == pullMsgRequest) {
            return responseWrite("0", "pullMsgRequest 不能为空", null);
        }

        Response<String> accessTokeResponse = getAccessToken(CommonConst.WECHAT_ENTERPRISE);
        if (0 == accessTokeResponse.getStatus()) {
            return accessTokeResponse;
        }

        String requestUrl = PULL_MESSAGE_URL.replace("ACCESS_TOKEN", accessTokeResponse.getData());
        logger.info("pullMessage requestUrl: " + requestUrl);

        //当有传入消息类型的时候,从数据库查询相应的AgentId
        if (null != appEnum) {
            
            logger.info("RMIWechatServiceImpl env {}=============>>>>>>>>>>>>>>>>>>",evn);
            
            if(org.apache.commons.lang3.StringUtils.isNotEmpty(evn) && evn.equals("prod")){
                pullMsgRequest.setAgentid(appEnum.getProdAppId());
            }else{
                pullMsgRequest.setAgentid(appEnum.getTestAppId());
            }
            
        }
        logger.info("pullMessage pullMsgRequest:" + JsonTool.writeValueAsString(pullMsgRequest));

        PushMsgWeiXinRequest pullMsgWeiXinRequest = ConvertModel(pullMsgRequest);
        pullMsgWeiXinRequest.setTouser("@all");
        pullMsgWeiXinRequest.setSafe("0");
        pullMsgWeiXinRequest.setMsgtype("text");

        String jsonStr = HttpUtil.httpsRequest(requestUrl, "POST", JsonTool.writeValueAsString(pullMsgWeiXinRequest));

        PushMsgResponse pushMsgResponse = JSON.parseObject(jsonStr, PushMsgResponse.class);
        logger.info("WechatServiceImpl pushMsgResponse:" + pushMsgResponse);
        return responseWrite(null, pushMsgResponse);
    }

    @Override
    public Response<String> getOauth2AccessToken(Integer wechatkeyCode, String code) throws Exception {
        if (null == wechatkeyCode || 0 == wechatkeyCode) {
            return responseWrite("0", "wechatkeyCode 不能为空", null);
        }

        if (StringUtils.isEmpty(code)) {
            return responseWrite("0", "code 不能为空", null);
        }

        logger.info("getOauth2AccessToken code:" + code);
        //发送邀请
        WeixinConfigResponse weixinConfigModel = getWeixinConfigModel(wechatkeyCode);
        if (null == weixinConfigModel) {
            return responseWrite("0", "没有找到对应的公共号配置信息,请重新正确的wechatkeyCode", null);
        }
        String requestUrl = OAUTH2_ACCESS_TOKEN_URL.replace("APP_ID", weixinConfigModel.getAppId()).replace("APP_SECRET", weixinConfigModel.getAppSecret()).replace("CODE", code);
        String jsonStr = HttpUtil.httpsRequest(requestUrl, "GET", null);
        Oauth2AccessRequest oauth2AccessRequest = JSON.parseObject(jsonStr, Oauth2AccessRequest.class);
        if (null == oauth2AccessRequest.getAccess_token()) {
            return responseWrite("0", "getOauth2AccessToken 出错", jsonStr);
        }

        logger.info("getOauth2AccessToken result oauth2AccessRequest:" + oauth2AccessRequest);
        return responseWrite(null, oauth2AccessRequest);
    }

    public Response<String> getOauth2WeiXinUser(Integer wechatkeyCode, Oauth2AccessRequest oauth2AccessRequest) throws Exception {
        logger.info("RMIWechatServiceImpl getOauth2WeiXinUser enter wechatkeyCode ===>>>" + wechatkeyCode +
                "oauth2AccessRequest:" + FasterJsonTool.writeValueAsString(oauth2AccessRequest));
        if (null == wechatkeyCode || 0 == wechatkeyCode) {
            return responseWrite("0", "wechatkeyCode 不能为空", null);
        }

        if (StringUtils.isEmpty(oauth2AccessRequest.getOpenid())) {
            return responseWrite("0", "openId 不能为空", null);
        }

        if (StringUtils.isEmpty(oauth2AccessRequest.getAccess_token())) {
            return responseWrite("0", "access_token 不能为空", null);
        }

        // 如果请求成功
        String requestUrl = OAUTH2_WEIXIN_USER_URL.replace("ACCESS_TOKEN", oauth2AccessRequest.getAccess_token()).replace("OPENID", oauth2AccessRequest.getOpenid());
        String jsonStr = HttpUtil.httpsRequest(requestUrl, "GET", null);
        Oauth2WeixinUserRequest oauth2WeixinUserRequest = JSON.parseObject(jsonStr, Oauth2WeixinUserRequest.class);
        // 如果请求错误,返回提示
        if (null == oauth2WeixinUserRequest.getOpenid()) {
            return responseWrite("0", "网页授权获取用户信息失败.", jsonStr);
        }
        logger.info("getOauth2WeiXinUser result oauth2WeixinUserRequest:" + oauth2WeixinUserRequest);
        return responseWrite(null, oauth2WeixinUserRequest);
    }

    @Override
    public Response<String> messageTemplateSend(Integer wechatkeyCode, HashMap postBody) throws Exception {
        String jsonStr = httpClient(wechatkeyCode, postBody, SEND_TEMPLATE_MESSAGE_URL).getData();
        WeixinErrorResponse weixinErrorResponse = JSON.parseObject(jsonStr, WeixinErrorResponse.class);
        return responseWrite(null, weixinErrorResponse);
    }

    public Response<String> messageCustomSend(Integer wechatkeyCode, HashMap postBody) throws Exception {
        String jsonStr = httpClient(wechatkeyCode, postBody, MESSAGE_CUSTOM_SEND).getData();
        WeixinErrorResponse weixinErrorResponse = JSON.parseObject(jsonStr, WeixinErrorResponse.class);
        return responseWrite(null, weixinErrorResponse);
    }

    @Override
    public Response<String> messageCustomSendText(Integer wechatkeyCode, String openId, String content) throws Exception {
        HashMap postBody = new HashMap();
        postBody.put("touser", openId);
        postBody.put("msgtype", "text");

        HashMap postBodyText = new HashMap();
        postBodyText.put("content", content);

        postBody.put("text", postBodyText);

        return messageCustomSend(wechatkeyCode, postBody);
    }

    @Override
    public Response<String> messageTemplateSendCustomDSX(WeChatTemplateEnum templageEnum, WeChatModel msgModel) throws Exception {
        return null;
    }

    @Override
    public Response<String> getJsSDKConfigInfo(Integer wechatkeyCode, String requestUrl) throws Exception {
        Response<String> accessTokeResponse = getAccessToken(wechatkeyCode);
        if (0 == accessTokeResponse.getStatus()) {
            return accessTokeResponse;
        }

        WeixinConfigResponse weixinConfigModel = getWeixinConfigModel(wechatkeyCode);
        if (null == weixinConfigModel) {
            return responseWrite("0", "没有找到对应的公共号配置信息,请重新正确的wechatkeyCode", null);
        }

        String accesstoken = accessTokeResponse.getData();
        String appId = weixinConfigModel.getAppId();

        String getJsApiTicket = MemcacheKeys.getJsApiTicket;

        // 2. 从memcahe 获取 jsApiTicket
        String redisJsApiTicketKey = KeyWkbird.weiXinQYJsApiTicket.key(JS_APITICKET)+ "_" + weixinConfigModel.getAppId();
        String jsApiTicket = jedisCluster.get(redisJsApiTicketKey);
        logger.info("WechatServiceImpl  getJsApiTicket 1 get redis key:" + redisJsApiTicketKey + " value: " + jsApiTicket);
        if (jsApiTicket == null) {
            WeixinJsApiTickerResponse weixinJsApiTickerResponse = getJsApiTicket(accesstoken);
            jsApiTicket = weixinJsApiTickerResponse.getTicket();
            jedisCluster.set(redisJsApiTicketKey, jsApiTicket);
            jedisCluster.expire(redisJsApiTicketKey, EXPRIE_TIME); //30分钟后失效
            logger.info("WechatServiceImpl  getJsApiTicket 2 get redis key:" + redisJsApiTicketKey + " value: " + jsApiTicket);
        }

        // 3.jsSign 取值
        JSSignResponse jsSignResponse = jsSdkSign(requestUrl, jsApiTicket, appId);
        return responseWrite("1", null, jsSignResponse);
    }

    @Override
    public Response<String> getLasterMaterial(Integer wechatkeyCode, String fromUserName, String toUserName) throws Exception {
        if (StringUtils.isEmpty(fromUserName)) {
            return responseWrite("0", "fromUserName 不能为空", null);
        }

        if (StringUtils.isEmpty(toUserName)) {
            return responseWrite("0", "toUserName 不能为空", null);
        }

        String resultXml;

        //需要提交的参数
        HashMap postBody = new HashMap();
        postBody.put("type", "news");
        postBody.put("offset", 0);
        postBody.put("count", 1);

        //开始发送请求
        String jsonStr = httpClient(wechatkeyCode, postBody, GET_LASTER_MATERIAL).getData();
        JSONObject jsonObject = JSON.parseObject(jsonStr);

        //返回结构解析
        JSONArray itemsJSONArray = jsonObject.getJSONArray("item");
        NewsMessageResponse newsMessageResponse = new NewsMessageResponse();
        List<Article> articles = new ArrayList<Article>();
        if (itemsJSONArray.size() > 0) {
            JSONArray newsItemJSONArray = ((JSONObject) itemsJSONArray.get(0)).getJSONObject("content").getJSONArray("news_item");
            for (int j = 0; j < newsItemJSONArray.size(); j++) {
                Article article = new Article();
                JSONObject itemJSONObject = newsItemJSONArray.getJSONObject(j);
                article.setTitle(itemJSONObject.getString("title"));
                article.setDescription(itemJSONObject.getString("digest"));
                article.setPicUrl(itemJSONObject.getString("thumb_url"));
                article.setUrl(itemJSONObject.getString("url"));
                articles.add(article);
            }

            newsMessageResponse.setArticleCount(newsItemJSONArray.size());
            newsMessageResponse.setArticles(articles);
            resultXml = MessageUtil.ResNewsMessage(fromUserName, toUserName, articles);
        } else {
            TextMessageResponse textMessageResponse = new TextMessageResponse();
            textMessageResponse.setToUserName(toUserName);
            textMessageResponse.setFromUserName(fromUserName);
            textMessageResponse.setCreateTime(new Date().getTime());
            textMessageResponse.setMsgType(MessageUtil.RESP_MESSAGE_TYPE_TEXT);
            textMessageResponse.setFuncFlag(0);
            textMessageResponse.setContent("目前还没有素材");
            resultXml = MessageUtil.textMessageToXml(textMessageResponse);
        }

        logger.info("result: " + resultXml);
        return responseWrite("1", "访问成功", resultXml);
    }

    *//**
     * 获取JsApiTicket
     *
     * @param accessToken 唯一验证标识
     * @return 获取微信开发 js-sdk 唯一的票据
     * @author manson
     * @serialData 2016-01-26
     *//*
    public static WeixinJsApiTickerResponse getJsApiTicket(String accessToken) {
        String requestUrl = GET_TICKET_URL.replace("ACCESS_TOKEN", accessToken);
        String jsonStr = HttpUtil.httpsRequest(requestUrl, "GET", null);
        logger.info("RMIWechatServiceImpl getJsApiTicket jsonStr:" , jsonStr);
        return JSON.parseObject(jsonStr, WeixinJsApiTickerResponse.class);
    }

    *//**
     * 微信JS-SDK验证方法
     *
     * @param requestUrl  当前的访问页面
     * @param jsApiTicket 微信JS-SDK唯一票据
     * @return
     * @author manson
     * @Date 2016-01-26
     *//*
    public static JSSignResponse jsSdkSign(String requestUrl, String jsApiTicket, String appId) {
        String nonce_str = StringUtil.uuid();
        String timestamp = DateFormater.createTimestamp();
        String signUrl = "";
        String signature = "";

        // 需要加密的url
        signUrl = "jsapi_ticket=" + jsApiTicket + "&noncestr=" + nonce_str + "&timestamp=" + timestamp + "&url=" + requestUrl;
        logger.info("WechatUtil sign signUrl = " + signUrl);
        try {
            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(signUrl.getBytes("UTF-8"));
            signature = byteToHex(crypt.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            logger.error("WechatUtil wxJsSdkSign error NoSuchAlgorithmException" + e);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            logger.error("WechatUtil wxJsSdkSign error UnsupportedEncodingException" + e);
        }
        logger.info("WechatUtil sign signature = " + signature);

        // 赋值
        JSSignResponse jsSign = new JSSignResponse();
        jsSign.setAppid(appId);
        jsSign.setNonce_str(nonce_str);
        jsSign.setSignature(signature);
        jsSign.setTimestamp(timestamp);
        return jsSign;
    }

    *//**
     * 加密算法
     *
     * @param hash
     * @return
     *//*
    private static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    @Override
    public Response<String> qrcodeCreate(Integer wechatkeyCode, String senceid) throws Exception {

        if (null == wechatkeyCode || 0 == wechatkeyCode) {
            return responseWrite("0", "wechatkeyCode 不能为空", null);
        }

        if (StringUtils.isEmpty(senceid)) {
            return responseWrite("0", "senceid 不能为空", null);
        }

        //获取图片的ticket
        HashMap postBody = new HashMap();
        //postBody.put("expire_seconds", 604800);
        postBody.put("action_name", "QR_LIMIT_SCENE");

        HashMap postBodyScene = new HashMap();
        postBodyScene.put("scene_id", senceid);

        HashMap postBodyActionInfo = new HashMap();
        postBodyActionInfo.put("scene", postBodyScene);

        postBody.put("action_info", postBodyActionInfo);
        String jsonStr = httpClient(wechatkeyCode, postBody, QRCODE_CREATE_URL).getData();
        QRCodeCreateResponse qrCodeCreateResponse = JSON.parseObject(jsonStr, QRCodeCreateResponse.class);

        //通过ticket 换取图片
        byte[] imageCode = showqrCode(qrCodeCreateResponse.getTicket());
        FileKeyObject fileKeyObject = rmiFileService.uploadWeixinBarcode(imageCode, FileTypeEnum.JPG);
        return responseWrite(null, fileKeyObject);
    }


    @Override
    public Response<String> qrcodeCreate(Integer wechatkeyCode, HashMap postBody) throws Exception {
        String jsonStr = httpClient(wechatkeyCode, postBody, QRCODE_CREATE_URL).getData();
        QRCodeCreateResponse qrCodeCreateResponse = JSON.parseObject(jsonStr, QRCodeCreateResponse.class);
        return responseWrite(null, qrCodeCreateResponse);
    }

    @Override
    public byte[] showqrCode(String ticket) throws Exception {
        String requestUrl = QRCODE_SHOW_URL.replace("TICKET", ticket);
        return HttpUtil.httpRequestReturnImg(requestUrl, "GET", null);
    }

    @Override
    public Response<String> getOauth2Code(Integer wechatKeyCode, String redirectUrl) throws Exception {
        return getOauth2Code(wechatKeyCode, redirectUrl, CommonConst.OAUTH2_SNSAPI_BASE);
    }

    @Override
    public Response<String> getOauth2Code(Integer wechatKeyCode, String redirectUrl, String scope) throws Exception {
        WeixinConfigResponse weixinConfigModel = getWeixinConfigModel(wechatKeyCode);
        String getCodeRequestUrl = OAUTH2_AUTHORIZE_URL;
        getCodeRequestUrl = getCodeRequestUrl.replace("APPID", weixinConfigModel.getAppId());
        getCodeRequestUrl = getCodeRequestUrl.replace("REDIRECT_URI", HttpUtil.urlEnodeUTF8(redirectUrl));
        getCodeRequestUrl = getCodeRequestUrl.replace("SCOPE", scope); //获取用户所有基本信息
        logger.info("getOauth2Code getCodeRequestUrl:" + getCodeRequestUrl);
        return responseWrite("1", null, getCodeRequestUrl);
    }

    public Response<String> getWeiXinUser(Integer wechatkeyCode, String openId) throws Exception {
        if (null == wechatkeyCode || 0 == wechatkeyCode) {
            return responseWrite("0", "wechatkeyCode 不能为空", null);
        }

        if (StringUtils.isEmpty(openId)) {
            return responseWrite("0", "openId 不能为空", null);
        }

        Response<String> accessTokenResponse = getAccessToken(wechatkeyCode);
        if (0 == accessTokenResponse.getStatus()) {
            return responseWrite("0", accessTokenResponse.getMessage(), accessTokenResponse.getData());
        }
        String accessToken = accessTokenResponse.getData();
        // 如果请求成功
        String requestUrl = WEIXIN_USER_URL.replace("ACCESS_TOKEN", accessToken).replace("OPENID", openId);
        String jsonStr = HttpUtil.httpsRequest(requestUrl, "GET", null);
        WeixinUserRequest weixinUserRequest = JSON.parseObject(jsonStr, WeixinUserRequest.class);
        // 如果请求错误,返回提示
        if (null == weixinUserRequest.getOpenid()) {
            return responseWrite("0", "获取用户信息失败.", jsonStr);
        }
        return responseWrite(null, weixinUserRequest);
    }

    *//**
     * 获取微信的基本的appid 与 appSecret
     *
     * @param wechatKeyCode 微信的类型
     * @return
     *//*
    private WeixinConfigResponse getWeixinConfigModel(Integer wechatKeyCode) {
        String corpId = null;
        String corpSecret = null;
        String requestUrl = null;
        WeixinConfigResponse weixinConfigModel = null;
        switch (wechatKeyCode) {
            case CommonConst.WECHAT_ENTERPRISE:  //企业号: 服务找房
                corpId = Global.WECHAT_QY_APP_ID;
                corpSecret = Global.WECHAT_QY_APP_SECRET;
                requestUrl = ACCESS_TOKEN_ENTERPRISE_URL;
                break;
            case CommonConst.WECHAT_SUBSCRIBE:  //订阅号: 悟空找房
                corpId = Global.WECHAT_SUBSTRIBE_APPID;
                corpSecret = Global.WECHAT_SUBSTRIBE_APP_SECRET;
                requestUrl = ACCESS_TOKEN_URL;
                break;
            case CommonConst.WECHAT_SERVICE_WKZF: //服务号: 悟空找房
                corpId = Global.WECHAT_APP_ID;
                corpSecret = Global.WECHAT_APP_SECRET;
                requestUrl = ACCESS_TOKEN_URL;
                break;
            case CommonConst.WECHAT_SERVICE_DSX: //服务号: 我是大师兄
                corpId = Global.WECHAT_DSX_APP_ID;
                corpSecret = Global.WECHAT_DSX_APP_SECRET;
                requestUrl = ACCESS_TOKEN_URL;
                break;
            case CommonConst.WECHAT_SERVICE_WKZF_APP: //服务号: 悟空找房app使用的
                corpId = Global.WECHAT_WKZF_APP_ID;
                corpSecret = Global.WECHAT_WKZF_APP_SECRET;
                requestUrl = ACCESS_TOKEN_URL;
                break;

        }

        if (null != corpId && null != corpSecret && null != requestUrl) {
            weixinConfigModel = new WeixinConfigResponse();
            weixinConfigModel.setAppId(corpId);
            weixinConfigModel.setAppSecret(corpSecret);
            requestUrl = requestUrl.replace("APP_ID", corpId).replace("APP_SECRET", corpSecret);
            weixinConfigModel.setRequestURl(requestUrl);
        }

        logger.info("getWeixinConfigModel param: " + weixinConfigModel);
        return weixinConfigModel;

    }

    *//**
     * 转换成微信需要的模型
     *
     * @param sourceModel
     * @return
     *//*
    public PushMsgWeiXinRequest ConvertModel(PushMsgRequest sourceModel) {
        PushMsgWeiXinRequest pullMsgWeiXinRequest = new PushMsgWeiXinRequest();
        logger.info("ConvertModel sourceModel:" + JsonTool.writeValueAsString(sourceModel));
        try {
            BeanUtils.copyProperties(pullMsgWeiXinRequest, sourceModel);
            ContentResponse contentResponse = new ContentResponse();
            contentResponse.setContent(sourceModel.getContent());
            pullMsgWeiXinRequest.setText(contentResponse);

            logger.info("ConvertModel targetModel:" + JsonTool.writeValueAsString(pullMsgWeiXinRequest));
            return pullMsgWeiXinRequest;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }


    *//**
     * 根据AgentType 获取 AgentId
     *
     * @param agentType
     * @return
     *//*
    public String getQYAgentId(String agentType) {
        String agentId = "";
        WeixinQyAgent weixinQyAgent = weixinQyAgentReadMapper.getQyAgentIdByAgentType(agentType);
        if (null != weixinQyAgent) {
            agentId = weixinQyAgent.getAgentId().toString();
        }
        return agentId;
    }

    public Response<String> httpClient(Integer wechatkeyCode, HashMap postBody, String requestUrl) throws Exception {
        if (null == wechatkeyCode || 0 == wechatkeyCode) {
            return responseWrite("0", "wechatkeyCode 不能为空", null);
        }

        Response<String> accessTokeResponse = getAccessToken(wechatkeyCode);
        if (0 == accessTokeResponse.getStatus()) {
            return accessTokeResponse;
        }

        requestUrl = requestUrl.replace("ACCESS_TOKEN", accessTokeResponse.getData());
        String jsonStr = HttpUtil.httpsRequest(requestUrl, "POST", JSON.toJSONString(postBody));
        return responseWrite(null, jsonStr);
    }

}
*/