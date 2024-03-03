package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //1.从redis中查询商品缓存

        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        //2.取到数据判空，手动反序列化转换为对象，直接返回
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return  Result.ok(shop);
        }
        //3.代表没有锁定到数据，进行查mysql
        Shop shop = getById(id);
        if (shop==null){
            return  Result.fail("商户不存在");

        }
//        4.获取到数据同时写入redis缓存
        //手动序列化
        String shopStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,shopStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        log.debug(shop.toString());
        return Result.ok(shop);
    }
}
