package com.siqian.qianpicturebackend.model.enmus;

import cn.hutool.core.util.ObjUtil;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import lombok.Getter;

@Getter
public enum SpaceTypeEnum {

    PRIVATE("私有空间", 0 ),
    TEAM("团队空间", 1);

    private final String text;

    private final int value;



    /**
     * @param text 文本
     * @param value 值
     */
    SpaceTypeEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static SpaceTypeEnum getEnumByValue(Integer value) {
        ThrowUtils.throwIf(ObjUtil.isEmpty(value), ErrorCode.PARAMS_ERROR, "空间类型不存在");
        for (SpaceTypeEnum spaceLevelEnum : SpaceTypeEnum.values()) {
            if (spaceLevelEnum.value == value) {
                return spaceLevelEnum;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
    }
}

