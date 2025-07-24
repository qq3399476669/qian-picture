package com.siqian.qianpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.mapper.SpaceMapper;
import com.siqian.qianpicturebackend.model.dto.space.SpaceAddRequest;
import com.siqian.qianpicturebackend.model.dto.space.SpaceQueryRequest;
import com.siqian.qianpicturebackend.model.dto.space.analyze.*;
import com.siqian.qianpicturebackend.model.enmus.SpaceLevelEnum;
import com.siqian.qianpicturebackend.model.entity.Picture;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.SpaceVO;
import com.siqian.qianpicturebackend.model.vo.UserVO;
import com.siqian.qianpicturebackend.model.vo.space.analyze.*;
import com.siqian.qianpicturebackend.service.PictureService;
import com.siqian.qianpicturebackend.service.SpaceAnalyzeService;
import com.siqian.qianpicturebackend.service.SpaceService;
import com.siqian.qianpicturebackend.service.UserService;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;



/**
* @author lyj
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-06-01 18:25:22
*/
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceAnalyzeService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    /**
     * 获取空间使用情况分析
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        // 校验参数

        // 全空间和公共图库，需要配从picture表查询
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()){
            // 校验权限，仅管理员可以使用
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            // 统计图库的使用空间
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            // 补充查询范围
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
            List<Object> objects = pictureService.getBaseMapper().selectObjs(queryWrapper);
            long usageSize = objects.stream().mapToLong(obj -> (Long) obj).sum();
            long usageCount = objects.size();
            // 封装返回结果
            // 全空间和公共图库无数量和容量限制，也无比例
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usageSize);
            spaceUsageAnalyzeResponse.setUsedCount(usageCount);
            return spaceUsageAnalyzeResponse;
        }else {

            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            // 个人图库，需要从space表查询
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验权限
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0D / space.getMaxSize(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0D / space.getMaxCount(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);
            return spaceUsageAnalyzeResponse;
        }
    }

    /**
     * 获取空间分类分析
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验权限
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        // 分组查询
        queryWrapper.select("category", "count(*) as count", "sum(picSize) as totalSize")
                .groupBy("category");
        List<SpaceCategoryAnalyzeResponse> categoryAnalyzeResponseList = pictureService.getBaseMapper().selectMaps(queryWrapper).stream().map(obj -> {
            String category = "无分类";
            if (obj.get("category") != null) {
                category = (String) obj.get("category");
            }
            Long count = ((Number) obj.get("count")).longValue();
            Long totalSize = ((Number) obj.get("totalSize")).longValue();
            return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
        }).collect(Collectors.toList());

        return categoryAnalyzeResponseList;
    }

    /**
     * 获取空间标签分析
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);
        // 查询出所有的标签
        queryWrapper.select("tags");

        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                .map(Object::toString)
                .collect(Collectors.toList());
        // 解析标签并统计
        Map<String, Long> tagCountMap = tagsJsonList
                .stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        return tagCountMap.entrySet()
                .stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 获取空间大小分析
     * @param spaceSizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);
        // 查询出所有的大小
        queryWrapper.select("picSize");

        List<Long> picSizeList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                .map(size -> (Long) size)
                .collect(Collectors.toList());

        // 定义分段范围
        LinkedHashMap<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizeList.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizeList.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizeList.stream().filter(size -> size >= 500 * 1024 && size < 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizeList.stream().filter(size -> size >= 1 * 1024 * 1024 ).count());

        return sizeRanges.entrySet()
                .stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

    }

    /**
     * 获取空间用户分析
     * @param spaceUserAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        // 补充用户id查询
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        // 补充分析维度：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension){
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') as period", "count(*) as count");
                queryWrapper.groupBy("period");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) as period", "count(*) as count");
                queryWrapper.groupBy("period");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') as period", "count(*) as count");
                queryWrapper.groupBy("period");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间维度错误");
        }

        // 分组排序
        queryWrapper.groupBy("period").orderByAsc("period");

        // 查询并封装返回结果
        List<Map<String, Object>> mapList = pictureService.getBaseMapper().selectMaps(queryWrapper);

        List<SpaceUserAnalyzeResponse> userAnalyzeResponseList = mapList.stream().map(obj -> {
            String period = obj.get("period").toString();
            Long count = ((Number) obj.get("count")).longValue();
            return new SpaceUserAnalyzeResponse(period, count);
        }).collect(Collectors.toList());
        return userAnalyzeResponseList;

    }

    /**
     * 空间使用排行分析
     * @param spaceRankAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验权限
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        // 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByAsc("totalSize")
                .last("limit " + spaceRankAnalyzeRequest.getTopN());
        return spaceService.list(queryWrapper);
    }


    /**
     * 校验空间分析权限
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {

        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        // 对公共图片或全部图片分析，需要管理员权限
        if (queryPublic || queryAll){
            boolean isAdmin = userService.isAdmin(loginUser);
            ThrowUtils.throwIf(!isAdmin, ErrorCode.NO_AUTH_ERROR);
        }else {
            // 对个人图片分析，需要个人权限
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
    }


    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {

        // 全部空间分析
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        if (queryAll){
            return;
        }
        // 公共空间分析
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        if (queryPublic){

            queryWrapper.isNull("spaceId");
            return;
        }
        // 个人空间分析
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null){
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }



}




