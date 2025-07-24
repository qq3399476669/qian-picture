package com.siqian.qianpicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.siqian.qianpicturebackend.annotation.AuthCheck;
import com.siqian.qianpicturebackend.common.BaseResponse;
import com.siqian.qianpicturebackend.common.DeleteRequest;
import com.siqian.qianpicturebackend.common.ResultUtils;
import com.siqian.qianpicturebackend.constant.UserConstant;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.manager.auth.SpaceUserAuthManager;
import com.siqian.qianpicturebackend.model.dto.space.*;
import com.siqian.qianpicturebackend.model.enmus.SpaceLevelEnum;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.SpaceVO;
import com.siqian.qianpicturebackend.service.SpaceService;
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
@RequestMapping("/space")
public class SpaceController {


    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 创建空间
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        // 校验参数
        if (spaceAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 校验权限
        User loginUser = userService.getLoginUser(request);
        long spaceId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(spaceId);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deletespace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 校验参数
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        // 查看空间是否存在
        Space space = spaceService.getById(id);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 只有空间的创建者和管理员可以删除空间
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean removeResult = spaceService.removeById(id);
        ThrowUtils.throwIf(!removeResult, ErrorCode.OPERATION_ERROR, "空间删除失败");
        return ResultUtils.success(true);
    }

    /**
     * 更新空间(更改级别等)，仅管理员可用
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatespace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        // 校验参数
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = new Space();
        BeanUtil.copyProperties(spaceUpdateRequest, space);

        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);

        // 数据校验
        spaceService.validSpace(space, false);

        // 检查数据是否存在
        Space oldspace = spaceService.getById(spaceUpdateRequest.getId());
        ThrowUtils.throwIf(oldspace == null, ErrorCode.NOT_FOUND_ERROR);

        // 操作数据库
        boolean updateResult = spaceService.updateById(space);

        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "空间更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 根据id获取空间信息（管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getspaceById(Long id, HttpServletRequest request) {
        // 校验参数
        if (id == null || id <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询并返回结果
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(space);
    }

    /**
     * 根据id获取空间封装类信息（普通用户）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getspaceVOById(Long id, HttpServletRequest request) {
        // 校验参数
        if (id == null || id <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询并返回结果
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取权限列表篇
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
        spaceVO.setPermissionList(permissionList);
        return ResultUtils.success(spaceVO);
    }

    /**
     * 分页获取空间列表（管理员）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listspaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        // 校验参数
        if (spaceQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        int current = spaceQueryRequest.getCurrent();
        int size = spaceQueryRequest.getPageSize();
        Page<Space> page = new Page<>(current, size);
        Page<Space> spacePage = spaceService.page(page, spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间封装类列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listspaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);

        int current = spaceQueryRequest.getCurrent();
        int size = spaceQueryRequest.getPageSize();

        // 只允许一次最多查询20条，防爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        Page<Space> page = new Page<>(current, size);
        Page<Space> spacePage = spaceService.page(page, spaceService.getQueryWrapper(spaceQueryRequest));
        Page<SpaceVO> spaceVOPage = spaceService.getSpaceVOList(spacePage, request);
        return ResultUtils.success(spaceVOPage);
    }

    /**
     * 编辑空间（普通用户）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editspace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {

        User loginUser = userService.getLoginUser(request);
        // 校验参数
        if (spaceEditRequest == null || spaceEditRequest.getId() == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = new Space();
        BeanUtil.copyProperties(spaceEditRequest, space);
        // 填充审核参数
        spaceService.fillSpaceBySpaceLevel(space);
        // 设置编辑时间
        space.setEditTime(new Date());
        // 数据校验
        spaceService.validSpace(space, false);
        // 检查空间是否存在
        Space oldspace = spaceService.getById(space.getId());
        ThrowUtils.throwIf(oldspace == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人和管理员可以操作
        spaceService.checkSpaceAuth(loginUser, oldspace);
        // 操作数据库
        boolean updateResult = spaceService.updateById(space);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "空间编辑失败");
        return ResultUtils.success(true);
    }

    /**
     * 获取空间等级列表
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }


}
