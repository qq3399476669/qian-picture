package com.siqian.qianpicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在方法上加了这个注解
 * 如：@AuthCheck(mustRole = "admin")
 * 表明只有管理员权限的用户才有权限访问
 *
 */
@Target(ElementType.METHOD)  //注解作用于方法
@Retention(RetentionPolicy.RUNTIME)  //注解在运行时有效
public @interface AuthCheck {

    /**
     * 必须具有某个角色
     */
    String mustRole() default "";
}
