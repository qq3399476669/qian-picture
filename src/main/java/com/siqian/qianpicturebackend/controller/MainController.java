package com.siqian.qianpicturebackend.controller;

import com.siqian.qianpicturebackend.common.BaseResponse;
import com.siqian.qianpicturebackend.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 健康检测接口
     * @return
     */
    @GetMapping("/health")
    public BaseResponse health(){
        return ResultUtils.success("ok");
    }
}
