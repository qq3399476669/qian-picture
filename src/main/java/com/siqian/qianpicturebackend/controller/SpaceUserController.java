package com.siqian.qianpicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.siqian.qianpicturebackend.annotation.AuthCheck;
import com.siqian.qianpicturebackend.common.BaseResponse;
import com.siqian.qianpicturebackend.common.DeleteRequest;
import com.siqian.qianpicturebackend.common.ResultUtils;
import com.siqian.qianpicturebackend.constant.UserConstant;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.siqian.qianpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.siqian.qianpicturebackend.model.dto.space.*;
import com.siqian.qianpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.siqian.qianpicturebackend.model.dto.spaceuser.SpaceUserEditRequest;
import com.siqian.qianpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.siqian.qianpicturebackend.model.enmus.SpaceLevelEnum;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.SpaceUser;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.SpaceUserVO;
import com.siqian.qianpicturebackend.model.vo.SpaceVO;
import com.siqian.qianpicturebackend.service.SpaceService;
import com.siqian.qianpicturebackend.service.SpaceUserService;
import com.siqian.qianpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@RestController
@RequestMapping("/spaceUser")
public class SpaceUserController {


    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    /**
     * 创建团队空间
     */
    @PostMapping("/add")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
        // 校验参数
        if (spaceUserAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = spaceUserService.addSpaceUser(spaceUserAddRequest);
        return ResultUtils.success(id);
    }

    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 校验参数
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 删除
        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);

    }

    /**
     * 查询某个成员在某个空间的信息
     */
    @PostMapping("/get")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserQueryRequest==null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(ObjUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
        // 查询并返回结果
        SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }


    /**
     * 空间用户封装类列表
     */
    @PostMapping("/list")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUserVO(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);

        List<SpaceUser> spaceUserList = spaceUserService.list(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        List<SpaceUserVO> spaceUserVOList = spaceUserService.getSpaceUserVOList(spaceUserList);
        return ResultUtils.success(spaceUserVOList);
    }

    /**
     * 编辑
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest, HttpServletRequest request) {

        // 校验参数
        if (spaceUserEditRequest == null || spaceUserEditRequest.getId() == null || spaceUserEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        SpaceUser spaceUser = new SpaceUser();
        BeanUtil.copyProperties(spaceUserEditRequest, spaceUser);

        // 仅本人和管理员可以操作
        spaceUserService.validSpaceUser(spaceUser, false);

        // 检查空间是否存在
        SpaceUser oldSpaceUser = spaceUserService.getById(spaceUserEditRequest.getId());
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);

        // 操作数据库
        boolean updateResult = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 我加入的团队空间列表
     */
    @GetMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);

        // 查询列表
        List<SpaceUser> spaceUserList = spaceUserService.lambdaQuery().eq(SpaceUser::getUserId, loginUser.getId()).list();
        ThrowUtils.throwIf(CollectionUtil.isEmpty(spaceUserList), ErrorCode.NOT_FOUND_ERROR);

        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }


}
