package com.siqian.qianpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.siqian.qianpicturebackend.model.dto.space.SpaceAddRequest;
import com.siqian.qianpicturebackend.model.dto.space.SpaceQueryRequest;
import com.siqian.qianpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.siqian.qianpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.SpaceUserVO;
import com.siqian.qianpicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author lyj
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-07-07 16:39:30
*/
public interface SpaceUserService extends IService<SpaceUser> {
    /**
     * 创建空间成员
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 获取单个空间成员封装
     * @param spaceUser 空间成员对象
     * @param request 请求对象
     * @return 空间成员封装对象
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 列表获取空间成员封装
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 空间成员校验
     * @param spaceUser
     * @param add  是否为创建的时候校验
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间查询条件
     * @param spaceUserQueryRequest 空间查询请求参数
     * @return 空间查询条件
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);



}
