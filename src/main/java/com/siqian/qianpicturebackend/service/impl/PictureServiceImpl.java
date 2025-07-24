package com.siqian.qianpicturebackend.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.siqian.qianpicturebackend.api.aliyunAi.AliYunAiApi;
import com.siqian.qianpicturebackend.api.aliyunAi.model.CreateOutPaintingTaskRequest;
import com.siqian.qianpicturebackend.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.manager.CosManager;
import com.siqian.qianpicturebackend.manager.upload.FilePictureUpload;
import com.siqian.qianpicturebackend.manager.upload.PictureUploadTemplate;
import com.siqian.qianpicturebackend.manager.upload.UrlPictureUpload;
import com.siqian.qianpicturebackend.model.dto.file.UploadPictureResult;
import com.siqian.qianpicturebackend.model.dto.picture.*;
import com.siqian.qianpicturebackend.model.enmus.PictureReviewStatusEmu;
import com.siqian.qianpicturebackend.model.entity.Picture;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.PictureVO;
import com.siqian.qianpicturebackend.model.vo.UserVO;
import com.siqian.qianpicturebackend.service.PictureService;
import com.siqian.qianpicturebackend.mapper.PictureMapper;
import com.siqian.qianpicturebackend.service.SpaceService;
import com.siqian.qianpicturebackend.service.UserService;
import com.siqian.qianpicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author lyj
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-05-21 17:09:57
*/
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{


    @Resource
    private SpaceService spaceService;

    @Resource
    private CosManager cosManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    /**
     * 上传图片
     * @param inputSource 上传数据源(可以是本地图片、url)
     * @param pictureUploadRequest 上传图片请求
     * @param loginUser 登录用户
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 改为使用sa token权限校验
//            // 校验是否有空间权限，仅空间的所属人可以上传
//            if (!Objects.equals(space.getUserId(), loginUser.getId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验额度
            if(space.getTotalCount() >= space.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间容量不足");
            }
        }
        // 判断是新增还是更新
        Long pictureId = null;
        if (pictureUploadRequest.getId() != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 2.如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 改为使用sa token权限校验
//            // 仅本人和管理员可编图片
//            if (!Objects.equals(oldPicture.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 前端没传spaceId，则默认为旧图片的空间
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            }else {
                // 前端传入了spaceId  ，则校验是否一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片空间不一致");
                }
            }

            // 清除对象存储的旧图
            this.clearPictureFile(oldPicture);
        }
        // 3.新增或更新都需要上传图片，得到图片信息
        // 按照userId划分目录 =》 按照空间划分目录
        String uploadPathPrefix;
        // 如果spaceId为空，则默认为公共图库
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else {
            // 空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据上传的数据源类型执行不同的上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }

        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造写入数据库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 默认为解析出来的图片信息中的名称
        String picName = uploadPictureResult.getPicName();
        // 若前端传入名称，则设置为图片名称
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        picture.setSpaceId(spaceId); //指定空间id
        picture.setReviewStatus(PictureReviewStatusEmu.PASS.getValue());
        picture.setPicColor(uploadPictureResult.getPicColor());
        // 填充审核参数
        this.fillReviewParams(picture, loginUser);

        // 如果pictureId != null ，补充 id 和编辑时间 ，也就是更新操作； 否则是新增操作
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 写入数据库
            boolean saveOrUpdateResult = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!saveOrUpdateResult, ErrorCode.SYSTEM_ERROR, "图片上传失败，数据库操作失败");
            if (finalSpaceId != null){
                // 更新空间额度
                boolean update = spaceService.lambdaUpdate().eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.SYSTEM_ERROR, "额度更新失败");
            }
            return picture;
        });

        return PictureVO.objToVo(picture);
    }

    /**
     * 批量上传图片
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 1、校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        int picNum = pictureUploadByBatchRequest.getPicNum();
        ThrowUtils.throwIf(picNum > 30, ErrorCode.PARAMS_ERROR, "抓取图片不能超过30张");
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)){
            namePrefix = searchText;
        }
        // 2、抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            // 抓取的网页
            document = Jsoup.connect(fetchUrl).get();
        }catch (IOException e){
            log.error("抓取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "抓取页面失败");
        }
        // 3、解析内容
        Element div = document.getElementsByClass("dgControl").first();
        ThrowUtils.throwIf(ObjUtil.isEmpty(div), ErrorCode.OPERATION_ERROR, "获取元素失败");
        Elements imgElementList = div.select("img.mimg");
        // 4、遍历元素，依次上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            // 获取抓取的图片的url
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片地址，防止转义或者对象存储冲突的问题
            // 例如：codefather.cn?aaa=1，处理后为codefather.cn
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));

            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("上传图片成功，id = {}", pictureVO.getId());
                uploadCount++;
            }catch (Exception e){
                log.error("上传图片失败", e);
                continue;
            }
            // 判断是否达到需要抓取的图片的数量
            if (uploadCount >= picNum){
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 获取图片查询条件
     * @param pictureQueryRequest 图片查询请求参数
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        //  图片查询请求参数为空，之间返回空图片查询条件
        if (pictureQueryRequest == null){
            return queryWrapper;
        }
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();


        // 根据searchText内容，搜索 name 和 introduction 字段
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(
                        qw -> qw.like("name", searchText)
                                .or()
                                .like("introduction", searchText)
            );
        }
        // 根据其他字段进行查询
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(ObjUtil.isNotEmpty(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(ObjUtil.isNotEmpty(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >=开始时间
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < 结束时间
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // 根据tags进行查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 获取单个图片封装
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);

        Long userId = picture.getUserId();
        // 关联查询用户信息
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            // 将user转封装类
            UserVO userVo = userService.getUserVo(user);
            pictureVO.setUser(userVo);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装VO类
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOList(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        // 图片列表为空，直接返回空分页封装类
        if (CollUtil.isEmpty(pictureList)){
            return pictureVOPage;
        }

        // 将图片列表转封装类列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());

        // 查询关联用户
        // 获取全部的用户id
        Set<Long> userIdList = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 根据用户id列表查询用户
        List<User> userList = userService.listByIds(userIdList);
        Map<Long, List<User>> userIduserListMap = userList.stream().collect(Collectors.groupingBy(User::getId));
        // 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIduserListMap.containsKey(userId)) {
                user = userIduserListMap.get(userId).get(0);
                // 将user转封装类
                UserVO userVo = userService.getUserVo(user);
                pictureVO.setUser(userVo);
            }
        });
        pictureVOPage.setRecords(pictureVOList);

        return pictureVOPage;


    }

    @Override
    public void deletePicture(long pictureId, User loginUser){
        // 校验参数
        if (pictureId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);

        // 查看图片是否存在
        Picture picture = this.getById(pictureId);
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 已经改为使用注解校验权限
        // this.checkPictureAuth(picture, loginUser);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean removeResult = this.removeById(pictureId);
            ThrowUtils.throwIf(!removeResult, ErrorCode.SYSTEM_ERROR, "图片删除失败，数据库操作失败");
            if (picture.getSpaceId() != null){
                // 更新空间额度
                boolean update = spaceService.lambdaUpdate().eq(Space::getId, picture.getSpaceId())
                        .setSql("totalSize = totalSize - " + picture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.SYSTEM_ERROR, "额度更新失败");
            }

            // 并删除对象存储的数据
            this.clearPictureFile(picture);
            return true;
        });

    }

    /**
     * 编辑图片
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureEditRequest, picture);
        // 需要将tags字段单独转换
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 填充审核参数
        this.fillReviewParams(picture, loginUser);
        // 数据校验
        this.validPicture(picture);
        // 检查图片是否存在
        Picture oldPicture = this.getById(picture.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
//        // 仅本人和管理员可以操作
//        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//        }
        // 已经改为使用注解校验权限
        // this.checkPictureAuth(oldPicture, loginUser);
        // 操作数据库
        boolean updateResult = this.updateById(picture);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "图片编辑失败");
    }

    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR, "图片id列表不能为空");
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (CollUtil.isEmpty(pictureList)) {
            return;
        }
        // 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRole = pictureEditByBatchRequest.getNameRole();
        fillPictureWithNameRole(pictureList, nameRole);

        boolean result  = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    /**
     * 根据命名规则填充
     * nameRole格式： 图片{序号}
     * @param pictureList
     * @param nameRole
     */
    private void fillPictureWithNameRole(List<Picture> pictureList, String nameRole) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRole)) {
            return;
        }
        int count = 1;
        try {
            for (Picture picture : pictureList) {
                picture.setName(nameRole.replaceAll("\\{序号}", String.valueOf(count++)));
            }
        }catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "根据命名规则填充失败");
        }

    }

    /**
     * 根据颜色查询图片
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    @Override
    public List<PictureVO> searPictureByColor(long spaceId, String picColor, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf (spaceId <= 0 || picColor == null, ErrorCode.PARAMS_ERROR);
        // 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 查询该空间下的所有具有主色调字段的图片
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("spaceId", spaceId).isNotNull("picColor");
        List<Picture> pictureList = this.list(queryWrapper);
        if (CollUtil.isEmpty(pictureList)){
            return new ArrayList<>();
        }
        Color inputColor = Color.decode(picColor);
        // 转为Vo类
        List<PictureVO> sortedPictureList = pictureList.stream()
                .sorted(
                Comparator.comparingDouble(picture -> {
                    String pictureColor = picture.getPicColor();
                    // 若为图片颜色为空字符串，默认排序到最后
                    if (StrUtil.isBlank(pictureColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color color = Color.decode(pictureColor);

                    // 计算图片相似度
                    return -ColorSimilarUtils.calculateSimilarity(inputColor, color);
                }))
                .limit(12)
                .map(PictureVO::objToVo) // 转成VO类
                .collect(Collectors.toList());
        return sortedPictureList;
    }


    /**
     * 图片校验
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核
     * @param pictureReviewRequest 参数
     * @param loginUser 当前登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1、校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        PictureReviewStatusEmu statusEmu = PictureReviewStatusEmu.getPictureReviewStatusEmu(reviewStatus);
        if (id == null || id <= 0 || statusEmu == null || statusEmu.equals(PictureReviewStatusEmu.REVIEWING)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        Long picId = pictureReviewRequest.getId();
        // 2、查询图片是否存在
        Picture picture = this.getById(picId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 3、判断审核状态是否重复
        if (picture.getReviewStatus() == pictureReviewRequest.getReviewStatus()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 4、操作数据库
        Picture newPicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, newPicture);
        newPicture.setReviewerId(loginUser.getId());
        newPicture.setReviewTime(new Date());
        boolean b = this.updateById(newPicture);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEmu.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEmu.REVIEWING.getValue());
        }
    }

    /**
     * 清除对象存储中的图片
     * @param oldPicture
     */
    @Async   // 异步执行
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String oldPictureUrl = oldPicture.getUrl();
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("url", oldPictureUrl);
        long count = this.count(queryWrapper);
        // 如果有多条记录使用，则不删除
        if (count > 1){
            return;
        }
        // 如果只有一条记录使用，则删除
        // 删除图片
        cosManager.deleteObject(oldPictureUrl);
        // 删除缩略图
        String oldPictureThumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(oldPictureThumbnailUrl)){
            cosManager.deleteObject(oldPictureThumbnailUrl);
        }
    }

    /**
     * 图片权限校验
     * @param picture
     * @param lodinUser
     */
    @Override
    public void checkPictureAuth(Picture picture, User lodinUser) {
        Long spaceId = picture.getSpaceId();
        Long lodinUserId = lodinUser.getId();
        if (spaceId == null){
            // 公共图库，仅管理员和所属人可操作
            if (!userService.isAdmin(lodinUser) && !picture.getUserId().equals(lodinUserId)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }else {
            // 图片属于私有空间，所属人可操作
            if (!picture.getUserId().equals(lodinUserId)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 创建扩图任务
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     */
    @Override
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 已经改为使用注解校验权限
        // this.checkPictureAuth(picture, loginUser);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);

    }
}




