package com.siqian.qianpicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标签类别Vo
 */
@Data
public class PictureTagCategory implements Serializable {
    // 标签
    private List<String> tagList;

    // 类别
    private List<String> categoryList;


}
