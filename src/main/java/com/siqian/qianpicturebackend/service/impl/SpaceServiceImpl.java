package com.siqian.qianpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.manager.sharding.DynamicShardingManager;
import com.siqian.qianpicturebackend.mapper.SpaceMapper;
import com.siqian.qianpicturebackend.model.dto.space.SpaceAddRequest;
import com.siqian.qianpicturebackend.model.dto.space.SpaceQueryRequest;
import com.siqian.qianpicturebackend.model.enmus.SpaceLevelEnum;
import com.siqian.qianpicturebackend.model.enmus.SpaceRoleEnum;
import com.siqian.qianpicturebackend.model.enmus.SpaceTypeEnum;
import com.siqian.qianpicturebackend.model.entity.Picture;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.SpaceUser;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.PictureVO;
import com.siqian.qianpicturebackend.model.vo.SpaceVO;
import com.siqian.qianpicturebackend.model.vo.UserVO;
import com.siqian.qianpicturebackend.service.SpaceService;
import com.siqian.qianpicturebackend.service.SpaceUserService;
import com.siqian.qianpicturebackend.service.UserService;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


/**
* @author lyj
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-06-01 18:25:22
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{


    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private TransactionTemplate transactionTemplate;

    // 为方便部署，注释掉分库分表
//    @Resource
//    @Lazy
//    private DynamicShardingManager dynamicShardingManager;

    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1、填充默认参数
        Space space = new Space();

        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())){
            spaceAddRequest.setSpaceName("未命名空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null){
            spaceAddRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (spaceAddRequest.getSpaceType() == null){
            spaceAddRequest.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        BeanUtil.copyProperties(spaceAddRequest, space);
        // 填充容量和大小
        fillSpaceBySpaceLevel(space);
        // 2、参数校验
        validSpace(space, true);
        // 3、校验权限，非管理员只能创建普通级别的空间
        Long loginUserId = loginUser.getId();
        space.setUserId(loginUserId);
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new  BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }

        // 4、控制同一用户只能创建一个私有空间、以及一个团队空间
        String lock = String.valueOf(loginUserId).intern();
        synchronized (lock){
            Long execute = transactionTemplate.execute(status -> {
                // 判断是否已有空间
                boolean exist = this.lambdaQuery()
                        .eq(Space::getUserId, loginUserId)
                        .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                        .exists();
                if (exist){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "每个用户每类空间只能创建一个");
                }
                boolean save = this.save(space);
                ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");
                // 若创建的是团队空间、需要添加空间-用户记录
                if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()){
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(loginUserId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    boolean saved = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 若为旗舰空间，新建旗舰空间表
                // 为方便部署，先注释分表
//                dynamicShardingManager.createSpacePictureTable(space);

                return space.getId();
            });
            return Optional.ofNullable(execute).orElse(-1L);
        }
    }

    /**
     * 获取空间查询条件
     * @param spaceQueryRequest 空间查询请求参数
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        //  图片查询请求参数为空，之间返回空图片查询条件
        if (spaceQueryRequest == null){
            return queryWrapper;
        }

        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        // 根据其他字段进行查询
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);

        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 获取单个空间封装
     * @param space
     * @param request
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);

        Long userId = space.getUserId();
        // 关联查询用户信息
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            // 将user转封装类
            UserVO userVo = userService.getUserVo(user);
            spaceVO.setUser(userVo);
        }
        return spaceVO;
    }

    /**
     *  分页获取空间封装
     * @param spacePage
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSpaceVOList(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        // 图片列表为空，直接返回空分页封装类
        if (CollUtil.isEmpty(spaceList)){
            return spaceVOPage;
        }

        // 将图片列表转封装类列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());

        // 查询关联用户
        // 获取全部的用户id
        Set<Long> userIdList = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        // 根据用户id列表查询用户
        List<User> userList = userService.listByIds(userIdList);
        Map<Long, List<User>> userIduserListMap = userList.stream().collect(Collectors.groupingBy(User::getId));
        // 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIduserListMap.containsKey(userId)) {
                user = userIduserListMap.get(userId).get(0);
                // 将user转封装类
                UserVO userVo = userService.getUserVo(user);
                spaceVO.setUser(userVo);
            }
        });
        spaceVOPage.setRecords(spaceVOList);

        return spaceVOPage;
    }

    /**
     * 空间校验
     * @param space
     */
    @Override
    public void validSpace(Space space, boolean add) {
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Integer spaceType = space.getSpaceType();

        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);

        // 添加空间时的校验
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceLevel == null, ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            ThrowUtils.throwIf(spaceType == null, ErrorCode.PARAMS_ERROR, "空间类型不能为空");
        }

        // 编辑空间时的校验
        // 校验名称长度是否过长
        ThrowUtils.throwIf(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30, ErrorCode.PARAMS_ERROR, "空间名称长度不能超过30");

        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 校验空间级别
        ThrowUtils.throwIf(spaceLevel != null && spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "空间级别不合法");
        ThrowUtils.throwIf(spaceType != null && spaceTypeEnum == null, ErrorCode.PARAMS_ERROR, "空间类别不合法");
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别、自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            Long maxSize = space.getMaxSize();
            if (maxSize == null) {
                space.setMaxSize(spaceLevelEnum.getMaxSize());
            }
            Long maxCount = space.getMaxCount();
            if (maxCount == null) {
                space.setMaxCount(spaceLevelEnum.getMaxCount());
            }
        }
    }

    /**
     * 校验空间权限
     * @param loginUser
     * @param space
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人和管理员可以操作
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }


}




