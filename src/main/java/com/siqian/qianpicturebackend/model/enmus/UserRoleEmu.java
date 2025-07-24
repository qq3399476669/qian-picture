package com.siqian.qianpicturebackend.model.enmus;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRoleEmu {

    ADMIN("管理员", "admin"),
    USER("普通用户", "user");

    private final String text;

    private final String value;

    UserRoleEmu(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据值获取枚举
     * @param value
     * @return
     */
    public static UserRoleEmu getUserRoleEmu(String value) {
        if (ObjectUtil.isEmpty(value)){
            return null;
        }
        // 遍历枚举值
        for (UserRoleEmu userRoleEmu : UserRoleEmu.values()) {
            if (userRoleEmu.getValue().equals(value))
                return userRoleEmu;
        }

        return null;
    }

}
