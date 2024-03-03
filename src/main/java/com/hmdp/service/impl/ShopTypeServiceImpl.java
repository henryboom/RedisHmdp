package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


//    使用redis

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryList() {
//        1.取redis 查询
       String key= CACHE_SHOP_KEY;

//        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
//        //创建一个列表，接住从redis转换而来的字符串转换的列表
//        List<ShopType> typeList =null;
//
//        if (StrUtil.isNotBlank(shopTypeJson)){
//            //非空，进行反序列化，获取bean对象
//         typeList= JSONUtil.toList(shopTypeJson,ShopType.class);
//
//            return Result.ok(typeList);
//        }
////        2.redis获取不到，进行数据库查询
////        List<ShopType> typeList = query().orderByAsc("sort").list();
//        typeList=this.list(new LambdaQueryWrapper<ShopType>().orderByAsc(ShopType::getSort));
//
////        3.判断数据库中是否存在数据
//        if (Objects.isNull(typeList)){
//            return Result.fail("店铺类型不存在");
//
//        }
////        4.存在信息，写入Redis,并返回数据
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList),CACHE_TYPE_TTL,TimeUnit.MINUTES);


        //换一种方式，我们尝试将店铺类型列表数据转换为列表存储
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);//获取到的是字符串列表，也就是将每一个类型进行序列化为一个字符串
//        log.debug("list:"+list.toString());
        if(!list.isEmpty()){
            //手动反序列化
            List<ShopType> typeList = new ArrayList<>();
            for (String s : list) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }

        //2.从数据库内查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if(typeList.isEmpty()){
            return Result.fail("不存在该分类!");
        }
        //序列化
        for (ShopType shopType : typeList) {
            String s = JSONUtil.toJsonStr(shopType);
            list.add(s);
        }

        //3.存入缓存，这是以类似字符串数组存入，一个key对应一个字符串列表
        stringRedisTemplate.opsForList().rightPushAll(key, list);
        stringRedisTemplate.expire(key,CACHE_TYPE_TTL,TimeUnit.MINUTES);

        return Result.ok(typeList);

}
}