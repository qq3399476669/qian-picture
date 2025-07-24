package com.siqian.qianpicturebackend.common;

import com.siqian.qianpicturebackend.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用返回类
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;
    private T data;
    private String message;


    /**
     * 传状态码、响应数据、消息
     * @param code
     * @param data
     * @param message
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    /**
     * 传状态码、响应数据
     * @param code
     * @param data
     */
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    /**
     * 传错误枚举
     * @param errorCode
     */
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
