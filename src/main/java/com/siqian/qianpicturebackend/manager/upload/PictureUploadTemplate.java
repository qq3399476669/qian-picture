package com.siqian.qianpicturebackend.manager.upload;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.siqian.qianpicturebackend.config.CosClientConfig;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.manager.CosManager;
import com.siqian.qianpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 图片上传模板
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片
     * @param inputResource 输入源
     * @param uploadPathPrefix 上传路径前缀
     */
    public UploadPictureResult uploadPicture(Object inputResource, String uploadPathPrefix) {
        // 1.校验图片（抽象方法，由子类实现）
        vailPicture(inputResource);
        // 2.格式化图片上传地址（yyyy-MM-dd_uuid.文件后缀格式）
        // 日期,yyyy-MM-dd
        String date = DateUtil.formatDate(new Date());
        // uuid，防止重复
        String uuid = RandomUtil.randomString(16);
        // 获取原始文件名（抽象方法，有子类实现）
        String originalFilename = getOriginalFilename(inputResource);
        // 上传的文件格式化后的文件名
        String uploadFileName = String.format("%s_%s.%s", date, uuid, FileUtil.getSuffix(originalFilename));
        // 上传的路径 uploadPathPrefix + uploadFileName
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFileName);
        // 3.上传文件并获取图片信息
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            processFile(inputResource, file);
            // 上传文件
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获得图片处理的结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                // 获取压缩之后得到的文件信息
                CIObject compressedCiObject = objectList.get(0);

                // 缩略图默认就等于压缩图
                CIObject thumbnailCiObject = compressedCiObject;
                if (objectList.size() > 1){
                    // 获取缩略图的文件信息
                    thumbnailCiObject = objectList.get(1);
                }
                // 封装压缩图的返回结果
                return getUploadPictureInfo(originalFilename, compressedCiObject, thumbnailCiObject, imageInfo);
            }

            // 抽象方法（由子类实现）
            UploadPictureResult uploadPictureResult = getUploadPictureInfo(uploadPath, imageInfo, originalFilename, file);
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 4.清理临时文件
            deleteTempFile(file);
        }
    }



    /**
     * 校验图片
     * @param inputResource 输入源
     */
    public abstract void vailPicture(Object inputResource);

    /**
     * 获取原始文件名
     * @param inputResource 输入源
     */
    public abstract String getOriginalFilename(Object inputResource);

    /**
     *  处理输入源，并生成本地临时文件
     * @param inputResource
     * @param file
     */
    public abstract void processFile(Object inputResource, File file) throws Exception;

    /**
     * 获取图片信息
     * @param uploadPath
     * @param imageInfo
     * @param originalFilename
     * @param file
     * @return
     */
    private UploadPictureResult getUploadPictureInfo(String uploadPath, ImageInfo imageInfo, String originalFilename, File file) {
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        String picFormat = imageInfo.getFormat();
        double picScale = NumberUtil.round(picWidth * 1.0 /picHeight, 2).doubleValue();
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureResult.setPicName(FileUtil.getPrefix(originalFilename));
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(picFormat);
        uploadPictureResult.setPicColor(imageInfo.getAve());
        return uploadPictureResult;
    }

    /**
     * 获取经过压缩操作、与缩略之后的图片信息
     * @param originalFilename 原始文件名
     * @param compressedCiObject 压缩之后的文件对象
     * @param thumbnailCiObject 缩略之后的文件对象
     * @return
     */
    private UploadPictureResult getUploadPictureInfo(String originalFilename, CIObject compressedCiObject, CIObject thumbnailCiObject, ImageInfo imageInfo) {
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        String picFormat = compressedCiObject.getFormat();
        double picScale = NumberUtil.round(picWidth * 1.0 /picHeight, 2).doubleValue();
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        // 设置压缩后的原图地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        // 设置缩略图地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        uploadPictureResult.setPicName(FileUtil.getPrefix(originalFilename));
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(picFormat);
        uploadPictureResult.setPicColor(imageInfo.getAve());
        return uploadPictureResult;
    }

    /**
     * 清理临时文件
     * @param file 临时文件
     */
    public void deleteTempFile(File file) {
        if (file != null) {
            // 删除临时文件
            boolean re = file.delete();
            if (!re) {
                log.error("文件删除失败："+file.getAbsolutePath());
            }
        }
    }

}
