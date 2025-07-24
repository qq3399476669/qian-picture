package com.siqian.qianpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.siqian.qianpicturebackend.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.siqian.qianpicturebackend.common.PageRequest;
import com.siqian.qianpicturebackend.model.dto.picture.*;
import com.siqian.qianpicturebackend.model.dto.user.UserQueryRequest;
import com.siqian.qianpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author lyj
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-05-21 17:09:57
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     * @param inputSource 上传的图片
     * @param pictureUploadRequest 上传图片请求
     * @param loginUser 登录用户
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 批量图片上传
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     * 获取图片查询条件
     * @param pictureQueryRequest 图片查询请求参数
     * @return 图片查询条件
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);


    /**
     * 获取单个图片封装
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 分页获取图片封装
     */
    Page<PictureVO> getPictureVOList(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 删除图片
     * @param pictureId
     * @param loginUser
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 编辑图片
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    /**
     * 批量编辑图片
     */
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 根据颜色查询图片（使用欧氏距离查询）
     */
    List<PictureVO> searPictureByColor(long spaceId, String picColor, User loginUser);


    /**
     * 图片校验
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 清除对象存储中的图片
     * @param oldPicture
     */
    void clearPictureFile(Picture oldPicture);

    /**
     * 图片权限校验
     */
    void checkPictureAuth(Picture picture, User lodinUser);

    /**
     * 创建扩图任务
     */
    CreateOutPaintingTaskResponse createOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);

}
