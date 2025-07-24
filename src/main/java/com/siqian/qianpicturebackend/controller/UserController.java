package com.siqian.qianpicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.siqian.qianpicturebackend.annotation.AuthCheck;
import com.siqian.qianpicturebackend.common.BaseResponse;
import com.siqian.qianpicturebackend.common.DeleteRequest;
import com.siqian.qianpicturebackend.common.ResultUtils;
import com.siqian.qianpicturebackend.constant.UserConstant;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.model.dto.user.*;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.LoginUserVO;
import com.siqian.qianpicturebackend.model.vo.UserVO;
import com.siqian.qianpicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 注册
     * @param userRegisterRequest 用户注册参数
     * @return 用户id
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest){
        // 检验参数
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);

        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        // 调用service注册方法
        long result = userService.userRegister(userAccount, userPassword, checkPassword);

        return ResultUtils.success(result);
    }

    /**
     * 登录
     * @param userLoginRequest 用户登录参数
     * @param request http请求
     * @return 用户脱敏信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request){
        // 检验参数
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);

        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        // 调用service登录方法
        LoginUserVO loginUserVo = userService.userLogin(userAccount, userPassword, request);

        return ResultUtils.success(loginUserVo);
    }

    /**
     * 获取当前登录用户信息
     * @param request http请求
     * @return 用户脱敏信息
     */
    @GetMapping("/current")
    public BaseResponse<LoginUserVO> getCurrentLoginUser(HttpServletRequest request){
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 脱敏
        LoginUserVO loginUserVo = userService.getLoginUserVo(loginUser);
        return ResultUtils.success(loginUserVo);
    }

    /**
     * 用户注销
     * @param request http请求
     * @return 成功提示
     */
    @GetMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request){
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }


    /**
     * 创建用户
     * @param userAddRequest 用户创建的参数
     * @return 用户id
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 使用自定义注解，规定只有管理员才能创建用户
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest){
        // 检验参数
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 设置默认密码（加密后的）
        final String DEFAULT_USER_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_USER_PASSWORD);
        user.setUserPassword(encryptPassword);
        // 调用service创建用户方法
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据id获取用户（管理员使用）
     * @param id 用户id
     * @return 用户信息
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 使用自定义注解，规定只有管理员才能创建用户
    public BaseResponse<User> getUserById(long id){
        // 检验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询用户
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据id获取脱敏用户信息（普通用户使用）
     * @param id 用户id
     * @return 用户信息
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVoById(long id){
        BaseResponse<User> baseResponse = getUserById(id);
        User user = baseResponse.getData();
        // 脱敏
        UserVO userVo = userService.getUserVo(user);
        return ResultUtils.success(userVo);
    }

    /**
     * 删除用户
     * @param deleteRequest 删除请求参数
     * @return 成功提示
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 使用自定义注解，规定只有管理员才能创建用户
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest){
        // 参数校验
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        // 调用service删除用户方法
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     * @param userUpdateRequest 用户更新的参数
     * @return 用户id
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 使用自定义注解，规定只有管理员才能创建用户
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest){
        // 检验参数
        ThrowUtils.throwIf(userUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        // 调用service更新用户方法
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页查询用户
     * @param userQueryRequest 用户查询请求参数
     * @return 用户信息列表
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 使用自定义注解，规定只有管理员才能创建用户
    public BaseResponse<Page<UserVO>> listPageUserVo(@RequestBody UserQueryRequest userQueryRequest) {
        // 检验参数
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取分页参数
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        // 创建分页对象
        Page<User> page = new Page<>(current, pageSize);
        // 调用service分页查询用户方法
        Page<User> userPage = userService.page(page, userService.getQueryWrapper(userQueryRequest));
        // 脱敏
        Page<UserVO> userVoPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVoList = userService.getUserVoList(userPage.getRecords());
        userVoPage.setRecords(userVoList);
        return ResultUtils.success(userVoPage);
    }
}
