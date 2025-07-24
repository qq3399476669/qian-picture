package com.siqian.qianpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * url图片上传
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate{
    @Override
    public void vailPicture(Object inputResource) {
        String url = (String) inputResource;
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
                    final long MAX_PIC_SIZE = 1024 * 1024 * 5;
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

    @Override
    public String getOriginalFilename(Object inputResource) {
        String url = (String) inputResource;
        return FileUtil.mainName(url);
    }

    @Override
    public void processFile(Object inputResource, File file) throws Exception {
        String url = (String) inputResource;
        // 下载文件到临时目录
        HttpUtil.downloadFile(url, file);
    }
    // 定义一个名为UrlPictureUpload的公共类
}
