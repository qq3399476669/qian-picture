package com.siqian.qianpicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.siqian.qianpicturebackend.config.CosClientConfig;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 文件上传服务
 * @deprecated  已废弃，改为使用upload包中的模板方法
 */
@Service
@Slf4j
@Deprecated
public class FileManager {  
  
    @Resource  
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片
     * @param multipartFile 上传的文件
     * @param uploadPathPrefix 上传路径前缀
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 1.校验图片
        vailPicture(multipartFile);
        // 2.格式化图片上传地址（yyyy-MM-dd_uuid.文件后缀格式）
        // 日期,yyyy-MM-dd
        String date = DateUtil.formatDate(new Date());
        // uuid，防止重复
        String uuid = RandomUtil.randomString(16);
        // 原始文件名
        String originalFilename = multipartFile.getOriginalFilename();
        // 上传的文件格式化后的文件名
        String uploadFileName = String.format("%s_%s.%s", date, uuid, FileUtil.getSuffix(originalFilename));
        // 上传的路径 uploadPathPrefix + uploadFileName
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFileName);
        // 3.上传文件并获取图片信息
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            // 上传文件
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
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
     * @param multipartFile 上传的文件
     */
    private void vailPicture(MultipartFile multipartFile) {
        // 1.校验是否上传了图片
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 2.校验图片大小
        long picSize = multipartFile.getSize();
        final long MAX_PIC_SIZE = 1024 * 1024 * 2;
        ThrowUtils.throwIf(picSize > MAX_PIC_SIZE, ErrorCode.PARAMS_ERROR, "图片大小不能超过2M");
        // 3.校验图片格式
        // 允许的格式
        final List<String> PIC_SUFFIX = Arrays.asList("jpg", "png", "jpeg", "gif", "bmp", "webp");
        // 获取上传的文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!PIC_SUFFIX.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "图片格式不正确");

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


    /**
     * 通过url上传图片
     * @param url 图片的url
     * @param uploadPathPrefix 上传路径前缀
     */
    public UploadPictureResult uploadPictureByUrl(String url, String uploadPathPrefix) {
        // 1.校验图片（通过url进行校验）
        // todo
        vailPicture(url);
        // 2.格式化图片上传地址（yyyy-MM-dd_uuid.文件后缀格式）
        // 日期,yyyy-MM-dd
        String date = DateUtil.formatDate(new Date());
        // uuid，防止重复
        String uuid = RandomUtil.randomString(16);
        // 原始文件名 todo
//        String originalFilename = multipartFile.getOriginalFilename();
        String originalFilename = FileUtil.mainName(url);


        // 上传的文件格式化后的文件名
        String uploadFileName = String.format("%s_%s.%s", date, uuid, FileUtil.getSuffix(originalFilename));
        // 上传的路径 uploadPathPrefix + uploadFileName
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFileName);
        // 3.上传文件并获取图片信息
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);

//            multipartFile.transferTo(file);
            // 下载图片 todo
            HttpUtil.downloadFile(url, file);

            // 上传文件
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
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
     * 根据url校验校验
     */
    private void vailPicture(String url) {
        // 1、校验是否为空
        ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.PARAMS_ERROR);
        // 2、校验url格式
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "url格式不正确");
        }
        // 3、校验Url协议
        ThrowUtils.throwIf(!url.startsWith("http://") && !url.startsWith("https://"), ErrorCode.PARAMS_ERROR, "仅支持 HTTP 与 HTTPS 协议的url地址");
        // 4、发送head请求，验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, url).execute();
            // 未正常返回，无需执行其他判断
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }

            // 5、文件存在，文件类型校验
            String contentType = response.header("Content-Type");
            // 不为空才校验是否合法
            if (StrUtil.isNotBlank(contentType)){
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpg", "image/png", "image/jpeg", "image/gif", "image/bmp", "image/webp");
                // 获取上传的文件后缀
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType), ErrorCode.PARAMS_ERROR, "图片格式不正确");
            }
            // 6、文件存在，文件大小校验
            String contentLength = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLength)) {
                try{
                    long contentLengthLong = Long.parseLong(contentLength);
                    final long MAX_PIC_SIZE = 1024 * 1024 * 2;
                    ThrowUtils.throwIf(contentLengthLong > MAX_PIC_SIZE, ErrorCode.PARAMS_ERROR, "图片大小不能超过2M");
                }catch (NumberFormatException e){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }


}
