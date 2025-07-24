package com.siqian.qianpicturebackend.model.enmus;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 图片审核状态枚举
 */
@Getter
public enum PictureReviewStatusEmu {

    REVIEWING("待审核", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);

    private final String text;

    private final int value;

    PictureReviewStatusEmu(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据值获取枚举
     * @param value
     * @return
     */
    public static PictureReviewStatusEmu getPictureReviewStatusEmu(int value) {
        if (ObjectUtil.isEmpty(value)){
            return null;
        }
        // 遍历枚举值
        for (PictureReviewStatusEmu pictureReviewStatusEmu : PictureReviewStatusEmu.values()) {
            if (pictureReviewStatusEmu.getValue() == value)
                return pictureReviewStatusEmu;
        }

        return null;
    }

}
