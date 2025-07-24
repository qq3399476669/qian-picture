package com.siqian.qianpicturebackend.api.aliyunAi;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.siqian.qianpicturebackend.api.aliyunAi.model.CreateOutPaintingTaskRequest;
import com.siqian.qianpicturebackend.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.siqian.qianpicturebackend.api.aliyunAi.model.GetOutPaintingTaskResponse;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {

    @Value("${aliYunAi.apikey}")
    private String apiKey;

    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";


    // 创建任务
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        // 发送请求
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable")
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        // 处理响应
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            if (createOutPaintingTaskResponse.getCode() != null) {
                String errorMessage = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常：{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败:" + errorMessage);
            }
            return createOutPaintingTaskResponse;
        }
    }

    // 根据id查询创建的任务结果
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(taskId == null, ErrorCode.PARAMS_ERROR, "任务id不能为空");

        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        // 处理响应
        try (HttpResponse httpResponse = HttpRequest
                .get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}



