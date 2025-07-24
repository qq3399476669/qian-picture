package com.siqian.qianpicturebackend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import com.siqian.qianpicturebackend.manager.auth.SpaceUserAuthManager;
import com.siqian.qianpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.siqian.qianpicturebackend.model.enmus.SpaceTypeEnum;
import com.siqian.qianpicturebackend.model.entity.Picture;
import com.siqian.qianpicturebackend.model.entity.Space;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.service.PictureService;
import com.siqian.qianpicturebackend.service.SpaceService;
import com.siqian.qianpicturebackend.service.SpaceUserService;
import com.siqian.qianpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * websocket拦截器，建立连接前需限校验
 */
@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 建立链接前先校验
     * @param request
     * @param response
     * @param wsHandler
     * @param attributes 给 websocket session会话设置属性
     * @return
     * @throws Exception
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 获取当前登录用户
        if (request instanceof ServletServerHttpRequest){
            HttpServletRequest httpServletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 从请求中获取参数
            String pictureId = httpServletRequest.getParameter("pictureId");
            if (pictureId == null){
                log.error("缺少图片参数，拒绝握手");
                return false;
            }
            // 获取当前登录用户
            User loginUser = userService.getLoginUser(httpServletRequest);
            if (ObjUtil.isEmpty(loginUser)){
                log.error("未登录，拒绝握手");
                return false;
            }
            // 校验用户是否有编辑当前图片的权限
            Picture picture = pictureService.getById(Long.valueOf(pictureId));
            if (ObjUtil.isEmpty(picture)){
                log.error("图片不存在，拒绝握手");
                return false;
            }
            // 如果是团队空间，并且有编辑者权限，才能进行链接
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null){
                space = spaceService.getById(spaceId);
                if (ObjUtil.isEmpty(space)){
                    log.error("图片所在的空间不存在，拒绝握手");
                    return false;
                }
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()){
                    log.error("图片所在的空间不是团队空间，拒绝握手");
                    return false;
                }
            }
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)){
                log.error("用户没有编辑图片的权限，拒绝握手");
                return false;
            }
            // 设置用户登录信息等属性到websocket会话中
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId", Long.valueOf(pictureId));

        }


        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
