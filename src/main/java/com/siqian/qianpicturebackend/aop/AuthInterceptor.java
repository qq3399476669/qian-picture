package com.siqian.qianpicturebackend.aop;

import com.siqian.qianpicturebackend.annotation.AuthCheck;
import com.siqian.qianpicturebackend.exception.ErrorCode;
import com.siqian.qianpicturebackend.exception.ThrowUtils;
import com.siqian.qianpicturebackend.model.enmus.UserRoleEmu;
import com.siqian.qianpicturebackend.model.entity.User;
import com.siqian.qianpicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;


    /**
     * 为加了自定义@AuthCheck（）注解的方法进行增强，用于权限校验
     * @param joinPoint 切入点
     * @param authCheck 权限检验注解
     */
    @Around("@annotation(authCheck)") // 环绕通知
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1、获取注解中规定需要的权限值
        String mustRole = authCheck.mustRole();

        // 2、获取当前登录用户
        // 获取request对象
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取session中的用户信息
        User loginUser = userService.getLoginUser(request);

        // 3、权限校验
        UserRoleEmu mastRoleEmu = UserRoleEmu.getUserRoleEmu(mustRole);
        // 如果不需要权限，直接放行
        if (mastRoleEmu == null) {
            return joinPoint.proceed();
        }
        // 如果用户没有指定角色，说明用户没有权限，拒绝放行
        UserRoleEmu userRoleEmu = UserRoleEmu.getUserRoleEmu(loginUser.getUserRole());
        ThrowUtils.throwIf(userRoleEmu == null, ErrorCode.NO_AUTH_ERROR);
        // 如果必须有管理员权限，但是用户没有管理员权限，拒绝放行
        ThrowUtils.throwIf(mastRoleEmu == UserRoleEmu.ADMIN && !Objects.equals(userRoleEmu, UserRoleEmu.ADMIN), ErrorCode.NO_AUTH_ERROR);

        // 通过权限校验，放行
        return joinPoint.proceed();

    }

}
