package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.constant.JwtClaimsConstant;
import com.hmdp.dto.UserDTO;
import com.hmdp.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties ;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate , JwtProperties jwtProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtProperties = jwtProperties;
    }

//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1. 获取请求头中token
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            return true;
//        }
//        //2. token获取redis中用户
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
//                .entries(RedisConstants.LOGIN_USER_KEY + token);
//        //3. 判断用户是否存在
//        if (userMap.isEmpty()) {
//            return true;
//        }
//        //5.将查询到的Hash数据转为UserDTO对象
//        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        //6. 存在，保存用户信息到 ThreadLocal
//        UserHolder.saveUser(user);
//        //7. 刷新token有效期
//        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
//        //8. 放行
//        return true;
//    }
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        //移除用户
//        UserHolder.removeUser();
//    }
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    // 1.获取请求头中的token
//        String token = request.getHeader("authorization");
    String token = request.getHeader(jwtProperties.getUserTokenName());
    if (StrUtil.isBlank(token)) {
        return true;
    }
    Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(),token);
    Long userId =  claims.get(JwtClaimsConstant.USER_ID,Long.class);
    // 2.基于userId获取redis中的用户
    String key  = LOGIN_USER_KEY + userId;
    Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
    // 3.判断用户是否存在
    if (userMap.isEmpty()) {
        return true;
    }
    // 4.判断token是否一致,防止有以前生成的jwt，仍然能够登录
    String jwttoken = userMap.get("jwttoken").toString();
    if(!jwttoken.equals(token)){
        return true;
    }
    // 5.将查询到的hash数据转为UserDTO
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    // 6.存在，保存用户信息到 ThreadLocal
    UserHolder.saveUser(userDTO);
    // 7.刷新token有效期
    stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
    // 8.放行
    return true;
}
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
