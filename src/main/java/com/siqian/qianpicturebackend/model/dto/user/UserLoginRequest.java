package com.siqian.qianpicturebackend.model.dto.user;

import lombok.Data;

/**
 * 用户登录请求
 */
@Data
public class UserLoginRequest{

    // 账号
    private String userAccount;

    // 密码
    private String userPassword;
}
