package com.siqian.qianpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片修改请求，一般情况给普通用户使用
 */
@Data
public class PictureEditRequest implements Serializable {
  
    /**  
     * id  
     */  
    private Long id;  
  
    /**  
     * 图片名称  
     */  
    private String name;  
  
    /**  
     * 简介  
     */  
    private String introduction;  
  
    /**  
     * 分类  
     */  
    private String category;  
  
    /**  
     * 标签  
     */  
    private List<String> tags;

//    /**
//     * 空间id
//     */
//    private Long spaceId;

    private static final long serialVersionUID = 1L;  
}
