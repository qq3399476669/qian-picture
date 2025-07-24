package com.siqian.qianpicturebackend.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.siqian.qianpicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class CosManager {


    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;


    /**
     * 将本地文件上传到 COS
     * @param key 唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file){
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     * @param key 唯一键
     * @throws IOException
     */
    public COSObject getObject(String key) throws IOException {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }


    /**
     * 上传对象（附带图片信息）
     */
    public PutObjectResult putPictureObject(String key, File file){
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);

        // 对图片处理（获取基本信息也视为一种图片处理）
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);  // 是否返回原图信息 0 不返回 1 返回

        // 1、图片压缩（转成webp格式）
        String webpKey = FileUtil.mainName(key) + ".png";
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 规则
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setRule("imageMogr2/format/png");
        rules.add(compressRule);

        // 2、缩略图处理
        // 仅对大小超过20kb的图片进行缩略图处理
        if (file.length() > 5 * 1024 * 1024) {{
            // 拼接缩略图的路径
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            // 规则
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 512, 512));
            rules.add(thumbnailRule);
        }}

        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);

        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     * @param key 唯一键
     * @throws IOException
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}
