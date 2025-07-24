package com.siqian.qianpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.siqian.qianpicturebackend.constant.UserConstant;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.manager.auth.StpKit;
import com.siqian.qianpicturebackend.model.dto.user.UserQueryRequest;
import com.siqian.qianpicturebackend.model.enmus.UserRoleEmu;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.LoginUserVO;
import com.siqian.qianpicturebackend.model.vo.UserVO;
import com.siqian.qianpicturebackend.service.UserService;
import com.siqian.qianpicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author lyj
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-05-09 09:09:44
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {

    /**
     * 用户注册
     * @param userAccount 账号
     * @param userPassword 密码
     * @param checkPassword 校验密码
     * @return 用户id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1、检验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 校验账号是否包含特殊字符
        String validPattern = "^[a-zA-Z0-9_]+$";
        if (!userAccount.matches(validPattern)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号包含特殊字符");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        // 2、检查账号是否已存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }

        // 3、加密密码
        String encryptPassword = this.getEncryptPassword(userPassword);

        // 4、插入数据到数据库中
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("用户" + System.currentTimeMillis());
        user.setUserRole(UserRoleEmu.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();

    }

    /**
     * 用户登录
     * @param userAccount 账号
     * @param userPassword 密码
     * @param request request
     * @return 脱敏用户信息
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1、检验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 2、加密密码
        String encryptPassword = this.getEncryptPassword(userPassword);
        // 3、检查账号是否已存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.getOne(queryWrapper);
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        // 4、保存用户登录状态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        // 记录用户态到Sa-token中，便于空间鉴权时使用，注意保证该用户信息与SpringSession中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, user);
        // 5、脱敏返回
        return this.getLoginUserVo(user);
    }

    /**
     * 密码加密
     * @param  userPassword 密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 加盐
        final String SALT = "siqian";
        return DigestUtil.md5Hex((SALT + userPassword).getBytes());
    }

    /**
     * 获取脱敏后的登录用户信息
     * @param user 原始信息
     * @return 脱敏信息
     */
    @Override
    public LoginUserVO getLoginUserVo(User user) {

        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVo = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVo);
        return loginUserVo;
    }

    /**
     * 获取脱敏后的用户信息
     * @param user 原始信息
     * @return 脱敏信息
     */
    @Override
    public UserVO getUserVo(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVo = new UserVO();
        BeanUtil.copyProperties(user, userVo);
        return userVo;
    }

    /**
     * 获取脱敏后的用户信息列表
     * @param userList 原始信息列表
     * @return 脱敏信息
     */
    @Override
    public List<UserVO> getUserVoList(List<User> userList) {
        if (CollUtil.isEmpty(userList)){
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVo).collect(Collectors.toList());
    }

    /**
     * 获取用户查询条件
     * @param userQueryRequest 用户查询请求参数
     * @return 用户查询条件
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR, "用户查询请求参数为空");

        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotEmpty(id), "id", id);
        queryWrapper.like(ObjectUtil.isNotEmpty(userName), "userName", userName);
        queryWrapper.like(ObjectUtil.isNotEmpty(userAccount), "userAccount", userAccount);
        queryWrapper.like(ObjectUtil.isNotEmpty(userProfile), "userProfile", userProfile);
        queryWrapper.eq(ObjectUtil.isNotEmpty(userRole), "userRole", userRole);
        queryWrapper.orderBy(ObjectUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }



    /**
     * 从session中获取当前登录用户
     * @param request 请求
     * @return 当前登录用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {

        User loginUser = (User)request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        // 判断是否登录
        ThrowUtils.throwIf((loginUser == null || loginUser.getId() == null), ErrorCode.NOT_LOGIN_ERROR);

        // 从数据库查询（防止缓存中的数据和数据库中的不一致）
        loginUser = this.getById(loginUser.getId());
        ThrowUtils.throwIf((loginUser == null), ErrorCode.NOT_LOGIN_ERROR);

        return loginUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 判断是否登录
        Object loginUser = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        ThrowUtils.throwIf((loginUser == null), ErrorCode.OPERATION_ERROR, "注销失败，用户未登录");
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);

        return true;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEmu.ADMIN.getValue().equals(user.getUserRole());
    }

}




