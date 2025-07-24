package com.siqian.qianpicturebackend.api.imageSearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.siqian.qianpicturebackend.exception.BusinessException;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取以图搜图页面地址（step 1）
 */
@Slf4j
public class GetImagePageUrlApi {
    public static String getImagePageUrl(String imageUrl) {

        // 1、准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        // 获取当前时间戳
        long uptime = System.currentTimeMillis();
        // 请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;
        String acsToken = "1749356779130_1749436278611_AxCJ29W6ZC9jxANSInTPi7cwMzVIVPMN8QIRfO6sOp4pSd5nWMwssxgJ9LQfPIFiQE1dHFRoRbtGk5YAoRJ+HSF/U4GIg9KHk6i3bmb/+yEgWPGpwJ5+TMhM92ub0ZHnj/7h6w6wY18Gm7uBs4SDGoBwiC0KOnkI8VwY8cuzL8D+XkWF/RcPKH1W42P2n/nmh+ulsCf1wbddt6tY2cOSb9QDhD9XbtkKdClSBzkxpUzgGvqF1nIKNlchHu1K02/DgM90jHwIUtTv2DXj7zE6DMJYbKSrfNmYU5IYe6N+KHF1qudAx0iVp/Is3bXD8cuqOpoMl7ajE9ZrZ5JvhCqk1t4JbfzezFyQ2pR5c0PnPGPnqUGEy5Vo/kTKK8HzSfB9oVapO2IYuMKtpoidw2CcGphhcyFm1/nNEgQiapmuRmJ9J001eFNZS9LwgKqn7gZX";

        try{
            // 2、发送请求
            HttpResponse httpResponse = HttpRequest.post(url)
                    .form(formData)
                    .header("Acs-Token", acsToken)
                    .timeout(5000)
                    .execute();
            System.out.println(httpResponse);
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 解析响应
            String response = httpResponse.body();
            Map<String, Object> result = JSONUtil.toBean(response, Map.class);

            // 3、处理响应结果
            if (result == null || !Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            Map<String, Object> data = (Map<String, Object>)result.get("data");
            String rawUrl = data.get("url").toString(); // 原生url

            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);  // 需要解码
            if (StrUtil.isBlank(searchResultUrl)){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效的结果地址");
            }
            return searchResultUrl;
        }catch (Exception e){
            log.error("调用百度以图搜图接口失败：" + e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }

    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        String searchResultUrl = getImagePageUrl(imageUrl);
        System.out.println(searchResultUrl);

    }
}
