package com.siqian.qianpicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.MD5;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.siqian.qianpicturebackend.annotation.AuthCheck;
import com.siqian.qianpicturebackend.api.aliyunAi.AliYunAiApi;
import com.siqian.qianpicturebackend.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.siqian.qianpicturebackend.api.aliyunAi.model.GetOutPaintingTaskResponse;
import com.siqian.qianpicturebackend.api.imageSearch.ImageSearchApiFacade;
import com.siqian.qianpicturebackend.api.imageSearch.model.ImageSearchResult;
import com.siqian.qianpicturebackend.common.BaseResponse;
import com.siqian.qianpicturebackend.common.DeleteRequest;
import com.siqian.qianpicturebackend.common.PageRequest;
import com.siqian.qianpicturebackend.common.ResultUtils;
import com.siqian.qianpicturebackend.config.CosClientConfig;
import com.siqian.qianpicturebackend.constant.UserConstant;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.manager.CosManager;
import com.siqian.qianpicturebackend.manager.auth.SpaceUserAuthManager;
import com.siqian.qianpicturebackend.manager.auth.StpKit;
import com.siqian.qianpicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.siqian.qianpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.siqian.qianpicturebackend.model.dto.picture.*;
import com.siqian.qianpicturebackend.model.enmus.PictureReviewStatusEmu;
import com.siqian.qianpicturebackend.model.entity.Picture;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.PictureTagCategory;
import com.siqian.qianpicturebackend.model.vo.PictureVO;
import com.siqian.qianpicturebackend.service.PictureService;
import com.siqian.qianpicturebackend.service.SpaceService;
import com.siqian.qianpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {


    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceService spaceService;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 本地缓存
     */
    private final Cache<String, String> CAFFINE_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * 上传图片并获得图片信息
     */
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)  // 需要上传图片的权限
    public BaseResponse<PictureVO> uploadPicture(@RequestParam("file") MultipartFile multipartFile,
                                                 PictureUploadRequest pictureUploadRequest,
                                                 HttpServletRequest request) {
        // 获取当前登录的用户信息
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过url上传图片并获得图片信息
     */
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody
                                                 PictureUploadRequest pictureUploadRequest,
                                                 HttpServletRequest request) {
        // 获取当前登录的用户信息
        User loginUser = userService.getLoginUser(request);

        String url = pictureUploadRequest.getUrl();

        PictureVO pictureVO = pictureService.uploadPicture(url, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/upload/batch")
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody
                                                 PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                 HttpServletRequest request) {
        // 获取当前登录的用户信息
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    /**
     * 以图搜图
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody
                                                      SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        String pictureUrl = picture.getThumbnailUrl();
        List<ImageSearchResult> searchResults = ImageSearchApiFacade.searchImages(pictureUrl);
        return ResultUtils.success(searchResults);
    }

    /**
     * 根据颜色搜索图片
     */
    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody
                                                                        SearchPictureByColorRequest searchPictureByPictureRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByPictureRequest.getPicColor();
        Long spaceId = searchPictureByPictureRequest.getSpaceId();

        User loginUser = userService.getLoginUser(request);

        List<PictureVO> pictureVOList = pictureService.searPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(pictureVOList);
    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 校验参数
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(id, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片，仅管理员可用
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        // 校验参数
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureUpdateRequest, picture);
        // 需要将tags字段单独转换
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        User loginUser = userService.getLoginUser(request);
        // 填充审核参数
        pictureService.fillReviewParams(picture, loginUser);

        // 数据校验
        pictureService.validPicture(picture);

        // 检查数据是否存在
        Picture oldPicture = pictureService.getById(picture.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 操作数据库
        boolean updateResult = pictureService.updateById(picture);

        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "图片更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 根据id获取图片信息（管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(Long id, HttpServletRequest request) {
        // 校验参数
        if (id == null || id <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询并返回结果
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据id获取图片封装类信息（普通用户）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(Long id, HttpServletRequest request) {
        // 校验参数
        if (id == null || id <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询并返回结果
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        Space space = null;
        // 权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null){

            boolean b = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!b, ErrorCode.NO_AUTH_ERROR);

            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);

            // 已经改为上述的编程式鉴权
            // pictureService.checkPictureAuth(picture, userService.getLoginUser(request));
        }
        // 获取权限列表
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, userService.getLoginUser(request));
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（管理员）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 校验参数
        if (pictureQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();
        Page<Picture> page = new Page<>(current, size);
        Page<Picture> picturePage = pictureService.page(page, pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片封装类列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);

        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();

        // 只允许一次最多查询20条，防爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null){
            // 公开图库
            // 普通用户只能查询审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEmu.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        }else {
            // 私有空间
            boolean b = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!b, ErrorCode.NO_AUTH_ERROR);
            // 改为上述鉴权方式
//            User loginUser = userService.getLoginUser(request);
//            Space space = spaceService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
//            if (!space.getUserId().equals(loginUser.getId())){
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
        }

        Page<Picture> page = new Page<>(current, size);
        Page<Picture> picturePage = pictureService.page(page, pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOList(picturePage, request);
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 分页获取图片封装类列表(多级缓存缓存：caffeine本地缓存+redis分布式缓存)
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);

        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();

        // 只允许一次最多查询20条，防爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 普通用户只能查询审核通过的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEmu.PASS.getValue()  );

        // 1、先从本地缓存中查询是否存在存缓数据
        String jsonStr = JSONUtil.toJsonStr(pictureQueryRequest);
        String jsonStrMd5 = MD5.create().digestHex(jsonStr);
        String cacheKey = "qianPicture:listPictureVOByPage:" + jsonStrMd5;
        String cacheData = CAFFINE_CACHE.getIfPresent(cacheKey);
        // 本地命中缓存，则直接返回，没有则继续查询redis中有无缓存
        if (cacheData != null) {
            Page<PictureVO> resultData = JSONUtil.toBean(cacheData, Page.class);
            return ResultUtils.success(resultData);
        }

        // 2、本地缓存未命中，则查询redis缓存
        cacheData = stringRedisTemplate.opsForValue().get(cacheKey);
        // redis命中缓存，直接返回，否则继续向数据库查询
        if (cacheData != null) {
            // 设置到本地缓存
            CAFFINE_CACHE.put(cacheKey, cacheData);

            Page<PictureVO> resultData = JSONUtil.toBean(cacheData, Page.class);
            return ResultUtils.success(resultData);
        }

        // 3、 本地缓存与分布式redis缓存都没有命中，从数据库查询
        Page<Picture> page = new Page<>(current, size);
        Page<Picture> picturePage = pictureService.page(page, pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOList(picturePage, request);

        // 4、将查询结果存入缓存
        // 设置到本地缓存
        CAFFINE_CACHE.put(cacheKey, JSONUtil.toJsonStr(pictureVOPage));
        // 设置到redis缓存
        // 设置缓存数据过期时间5-10分钟（避免缓存雪崩）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(pictureVOPage), cacheExpireTime, TimeUnit.SECONDS);

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 分页获取图片封装类列表(caffeine本地缓存)
     */
    @PostMapping("/list/page/vo/caffeine")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCaffeineCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);

        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();

        // 只允许一次最多查询20条，防爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 普通用户只能查询审核通过的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEmu.PASS.getValue()  );

        // 先从缓存中查询是否存在存缓数据，有则直接返回，没有则从数据库查询
        String jsonStr = JSONUtil.toJsonStr(pictureQueryRequest);
        // 对查询参数进行md5加密，减少存储
        String jsonStrMd5 = MD5.create().digestHex(jsonStr);
        String cacheKey = "listPictureVOByPage:" + jsonStrMd5;

        String cacheData = CAFFINE_CACHE.getIfPresent(cacheKey);
        // 如果本地缓存中存在数据，则直接返回
        if (cacheData != null) {
            Page<PictureVO> resultData = JSONUtil.toBean(cacheData, Page.class);
            return ResultUtils.success(resultData);
        }

        // 从数据库查询
        Page<Picture> page = new Page<>(current, size);
        Page<Picture> picturePage = pictureService.page(page, pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOList(picturePage, request);

        // 将查询结果存入本地缓存
        CAFFINE_CACHE.put(cacheKey, JSONUtil.toJsonStr(pictureVOPage));

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（普通用户）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        // 校验参数
        if (pictureEditRequest == null || pictureEditRequest.getId() == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量编辑图片
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        // 校验参数
        if (pictureEditByBatchRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 获取预置标签和类别
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 图片审核
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建AI扩图任务
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createOutPaintingTask(@RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse createOutPaintingTaskResponse = pictureService.createOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(createOutPaintingTaskResponse);
    }

    /**
     * 查询AI扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getOutPaintingTask(String taskId) {
        // 校验参数
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR);

        GetOutPaintingTaskResponse paintingTask = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(paintingTask);
    }

}
