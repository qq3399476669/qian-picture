package com.siqian.qianpicturebackend.controller;

import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.utils.IOUtils;
import com.siqian.qianpicturebackend.annotation.AuthCheck;
import com.siqian.qianpicturebackend.common.BaseResponse;
import com.siqian.qianpicturebackend.common.ResultUtils;
import com.siqian.qianpicturebackend.config.CosClientConfig;
import com.siqian.qianpicturebackend.constant.UserConstant;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.manager.CosManager;
import com.siqian.qianpicturebackend.model.enmus.UserRoleEmu;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 测试上传文件功能
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> uploadFileTest(@RequestParam("file") MultipartFile multipartFile){
        // 文件目录
        String fileName = multipartFile.getOriginalFilename();
        String filePath = String.format("/test/%s",fileName);
        File file = null;
        try {

            // 上传文件
            file = File.createTempFile(filePath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filePath, file);
            // 返回可以访问的图片地址
            String url = cosClientConfig.getHost() + filePath;
            return ResultUtils.success(url);
        } catch (Exception e) {
            log.error("上传文件失败"+filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传文件失败");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean re = file.delete();
                if (!re) {
                    log.error("文件删除失败："+filePath);
                }
            }

        }
    }

    /**
     * 测试下载文件功能
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/download")
    public void downFileTest(String filePath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInputStream = null;
        try {
            cosObjectInputStream = cosManager.getObject(filePath).getObjectContent();
            byte[] byteArray = IOUtils.toByteArray(cosObjectInputStream);

            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + filePath);
            // 写入响应
            response.getOutputStream().write(byteArray);
            response.getOutputStream().flush();

        } catch (Exception e) {
            log.error("上传下载失败"+filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传下载失败");
        } finally {
            if (cosObjectInputStream != null) {
                // 删除临时文件
                cosObjectInputStream.close();
            }
        }
    }
}
