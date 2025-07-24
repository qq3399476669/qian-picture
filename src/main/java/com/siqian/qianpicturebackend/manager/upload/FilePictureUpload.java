package com.siqian.qianpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 本地图片上传
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate{

    /**
     * 本地图片上传的校验
     * @param inputResource 输入源
     */
    @Override
    public void vailPicture(Object inputResource) {
        MultipartFile multipartFile = (MultipartFile) inputResource;
        // 1.校验是否上传了图片
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 2.校验图片大小
        long picSize = multipartFile.getSize();
        final long MAX_PIC_SIZE = 1024 * 1024 * 5;
        ThrowUtils.throwIf(picSize > MAX_PIC_SIZE, ErrorCode.PARAMS_ERROR, "图片大小不能超过2M");
        // 3.校验图片格式
        // 允许的格式
        final List<String> PIC_SUFFIX = Arrays.asList("jpg", "png", "jpeg", "gif", "bmp", "webp");
        // 获取上传的文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!PIC_SUFFIX.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "图片格式不正确");

    }

    /**
     * 获取本地图片的文件名
     * @param inputResource 输入源
     * @return
     */
    @Override
    public String getOriginalFilename(Object inputResource) {
        MultipartFile multipartFile = (MultipartFile) inputResource;
        return multipartFile.getOriginalFilename();
    }

    /**
     *  处理输入源，并生成本地临时文件
     * @param inputResource
     * @param file
     */
    @Override
    public void processFile(Object inputResource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputResource;
        multipartFile.transferTo(file);
    }
}
