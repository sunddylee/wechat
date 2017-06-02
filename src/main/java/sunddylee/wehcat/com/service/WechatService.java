package sunddylee.wehcat.com.service;

import javax.servlet.http.HttpServletRequest;


public interface WechatService {

    String processRequest(HttpServletRequest request);

  /*  JSSignResponse getJsSign(String requestUrl) throws Exception;

    Oauth2WeixinUserRequest getOauth2WeiXinUser(Oauth2AccessRequest oauth2AccessRequest) throws Exception;

    Oauth2AccessRequest getOauth2AccessToken(String code) throws Exception;

    String getOauth2Code(String requestUrl) throws Exception;

    ImageKeyObject mediaFetch(String mediaId) throws Exception;
    
    JSSignResponse getJsSign(String requestUrl,Integer wechatCode) throws Exception;*/
}
