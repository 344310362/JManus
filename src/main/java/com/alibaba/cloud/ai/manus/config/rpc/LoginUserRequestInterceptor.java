package com.alibaba.cloud.ai.manus.config.rpc;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoginUserRequestInterceptor implements RequestInterceptor {
    public static final String LOGIN_USER_TOKEN_HEADER = "Authorization";
    public static final String LOGIN_USER_HEADER = "login-user";
    private static final String REQUEST_ATTRIBUTE_LOGIN_USER_TYPE = "login_user_type";
    public static final String HEADER_TENANT_ID = "tenant-id";
    @Override
    public void apply(RequestTemplate requestTemplate) {
        String token = AuthContext.getToken();

        if (token != null && !token.isEmpty()) {
            System.out.println("Authorization: " + token);
            requestTemplate.header(LOGIN_USER_TOKEN_HEADER, token);
            requestTemplate.header(REQUEST_ATTRIBUTE_LOGIN_USER_TYPE, "2");
            requestTemplate.header(HEADER_TENANT_ID, "1");
            //requestTemplate.header(LOGIN_USER_HEADER, token);
        }
    }

}
