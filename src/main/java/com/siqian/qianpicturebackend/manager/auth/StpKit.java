package com.siqian.qianpicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * StpLogic 门面类，管理项目中所有的StpLogic账号体系
 */
@Component
public class StpKit {

    public static final String SPACE_TYPE = "space";


    /**
     * 默认原生会话对象，项目中未使用到
     */
    public static final StpLogic DEFAULT = StpUtil.stpLogic;

    /**
     * 空间账号体系
     */
    public static final StpLogic SPACE = new StpLogic(SPACE_TYPE);
}
