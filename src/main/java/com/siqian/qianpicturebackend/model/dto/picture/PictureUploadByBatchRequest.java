package com.siqian.qianpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadByBatchRequest implements Serializable {
  
    /**  
     * 需要抓取的图片的关键词
     */  
    private String searchText;

    /**
     * 需要抓取的图片数量（默认10条）
     */
    private int picNum = 10;

    /**
     * 名称前缀
     */
    private String namePrefix;


    private static final long serialVersionUID = 1L;  
}
