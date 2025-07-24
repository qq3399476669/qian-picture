package com.siqian.qianpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.baomidou.mybatisplus.extension.service.IService;

import com.siqian.qianpicturebackend.model.dto.space.SpaceAddRequest;
import com.siqian.qianpicturebackend.model.dto.space.SpaceQueryRequest;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author lyj
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-06-01 18:25:22
*/
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 获取空间查询条件
     * @param spaceQueryRequest 空间查询请求参数
     * @return 空间查询条件
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);


    /**
     * 获取单个空间封装
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 分页获取空间封装
     */
    Page<SpaceVO> getSpaceVOList(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 空间校验
     * @param space
     * @param add  是否为创建的时候校验
     */
    void validSpace(Space space, boolean add);


    /**
     * 填充空间容量、与图片数量
     * @param space
     */
    public void fillSpaceBySpaceLevel(Space space);


    /**
     * 校验空间权限
     */
    void checkSpaceAuth(User loginUser, Space space);
}
