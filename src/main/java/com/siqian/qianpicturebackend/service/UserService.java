package com.siqian.qianpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.siqian.qianpicturebackend.model.dto.user.UserQueryRequest;
import com.siqian.qianpicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.siqian.qianpicturebackend.model.vo.LoginUserVO;
import com.siqian.qianpicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author lyj
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-05-09 09:09:44
*/
public interface UserService extends IService<User> {


    /**
     * 用户注册
     * @param userAccount 账号
     * @param userPassword 密码
     * @param checkPassword 确认密码
     * @return 新用户id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户注册
     * @param userAccount 账号
     * @param userPassword 密码
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     * @param request 请求
     * @return 当前登录用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户登出
     * @param request 请求
     * @return 登出结果
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 加密密码
     * @param userPassword 密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取脱敏后的登录用户信息
     * @param user 原始信息
     * @return 脱敏信息
     */
    LoginUserVO getLoginUserVo(User user);

    /**
     * 获取脱敏后的用户信息
     * @param user 原始信息
     * @return 脱敏信息
     */
    UserVO getUserVo(User user);

    /**
     * 获取脱敏后的用户信息列表
     * @param userList 原始信息列表
     * @return 脱敏信息
     */
    List<UserVO> getUserVoList(List<User> userList);

    /**
     * 获取用户查询条件
     * @param userQueryRequest 用户查询请求参数
     * @return 用户查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);


}
