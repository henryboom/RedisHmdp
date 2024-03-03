package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录拦截器
 *
 * @author CHEN
 * @date 2022/10/07
 */
public class LoginInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //获取用户
//        if (UserHolder.getUser() == null) {
//            //不存在用户 拦截
//            response.setStatus(401);
//            return false;
//        }
//        //存在用户放行
//        return true;
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){
            //不存在，拦截 设置响应状态吗为401（未授权）
            response.setStatus(401);
            return false;
        }
        //2.基于token获取redis中用户
        String key=RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        if (userMap.isEmpty()){
            //4.不存在则拦截，设置响应状态吗为401（未授权）
            response.setStatus(401);
            return false;
        }
        //5.将查询到的Hash数据转化为UserDTO对象
        UserDTO userDTO=new UserDTO();
        BeanUtil.fillBeanWithMap(userMap,userDTO, false);
        //6.保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //7.更新token的有效时间，只要用户还在访问我们就需要更新token的存活时间
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        //8.放行
        return true;
    }


}
