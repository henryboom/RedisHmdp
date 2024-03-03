package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>

 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //接受到手机号，进行校验
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合手机号校验
            return Result.fail("手机号格式错误");
        }

        //手机号符合,生成验证码

        String code = RandomUtil.randomNumbers(6);//保存验证码到session
//        session.setAttribute("code", code);
//        4.保存到redis中,5分钟过期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.info("验证码：{}",code);
        log.debug("发送短信验证码成功!");

        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        // 1、判断手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        // 2、判断验证码是否正确
        //从redis中去除string类型的
        String redisCode  = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(redisCode)) {
            return Result.fail("验证码不正确");
        }

        // 3、判断手机号是否是已存在的用户
//        User user = this.getOne(new LambdaQueryWrapper<User>()
//                .eq(User::getPhone, phone));

        User user = baseMapper
                .selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, phone));
        if (Objects.isNull(user)) {
            // 用户不存在，需要注册
            user = createUserWithPhone(phone);
        }
        // 4、保存用户信息到Redis中，便于后面逻辑的判断（比如登录判断、随时取用户信息，减少对数据库的查询）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 将对象中字段全部转成string类型，StringRedisTemplate只能存字符串类型的数据
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>()
                , CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (name, value) -> value.toString()
                        ));
        //生成token,作为对象的键值对
        String token = UUID.randomUUID().toString(true);
        //保存信息到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);

        log.debug("登陆成功");
        return  Result.ok(token);
    }

    /**
     * 根据手机号创建用户并保存
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
