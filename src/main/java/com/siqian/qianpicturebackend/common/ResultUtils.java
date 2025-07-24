package com.siqian.qianpicturebackend.common;

import com.siqian.qianpicturebackend.exception.ErrorCode;

/**
 * 通用返回工具类
 */
public class ResultUtils {

    /**
     * 成功
     * @param data 返回数据
     * @return BaseResponse
     * @param <T> 泛型
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 失败
     * @param code 状态码
     * @param message 消息
     * @return BaseResponse
     */
    public static BaseResponse<?> fail(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }

    /**
     *
     * @param errorCode 状态枚举
     * @return BaseResponse
     */
    public static BaseResponse<?> fail(ErrorCode errorCode){
        return new BaseResponse<>(errorCode.getCode(), null, errorCode.getMessage());
    }

    /**
     *
     * @param errorCode 状态枚举
     * @return BaseResponse
     */
    public static BaseResponse<?> fail(ErrorCode errorCode, String message){
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }

}
