package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 自己注入自己为了获取代理对象 @Lazy 延迟注入 避免形成循环依赖
     */
    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //    private static final BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();



//创建lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀优惠券  --乐观锁
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始，是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束!");
        }
        //3.判断库存是否充足
        if(voucher.getStock()<=0){
            return Result.fail("优惠券库存不足!");
        }
        //4.扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock -1")
//                .eq("voucher_id", voucherId).update();

        //4.扣减库存,判断库存变化
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock -1")
//                .eq("voucher_id", voucherId).eq("stock",voucher.getStock())//where id = ? and stock =? 添加了乐观锁
//                .update();
//        由userId作为锁，但是不能将锁传递到方法里，这里使用intern:在字符串常量池寻找相同 的字符串
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            IVoucherOrderService proxy =  (IVoucherOrderService) AopContext.currentProxy();//获取代理对象
            return proxy.createVoucherOrder(voucherId);
        }

    }


    /**
     * 创建订单
     *
     * @param voucherId
     * @return
     */
@Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //实现一人一单
        Long userId =UserHolder.getUser().getId();
        if(query().eq("user_id",userId).eq("voucher_id", voucherId).count()>0){
            return Result.fail("已经购买过一次了");

        }
//    4.扣减库存，改一下大于零就可以买，而不是看数据是否变动
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId).gt("stock",0)//where id = ? and stock >0 添加了乐观锁
                .update();
        //5.创建订单
        if(!success){
            return Result.fail("优惠券库存不足!");
        }
        //6.返回订单id
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2用户id
//        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //6.3代金券id
        voucherOrder.setVoucherId(voucherId);

        //7.订单写入数据库
        save(voucherOrder);

        //8.返回订单Id
        return Result.ok(orderId);
    }
/**
     * 抢秒杀券，普通写法，只是完成基本逻辑，未优化并发性能
     *
     *      * @param voucherId 券id
     *      * @return {@link Result}
     *
     *     **/
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1、查询秒杀券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2、判断秒杀券是否合法
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 秒杀券的开始时间在当前时间之后
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 秒杀券的结束时间在当前时间之前
//            return Result.fail("秒杀已结束");
//        }
//        if (voucher.getStock() < 1) {
//            return Result.fail("秒杀券已抢空");
//        }
//        // 5、秒杀券合法，则秒杀券抢购成功，秒杀券库存数量减一
//        boolean flag = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
//                .eq(SeckillVoucher::getVoucherId, voucherId)
//                .setSql("stock = stock -1"));
//        if (!flag){
//            throw new RuntimeException("秒杀券扣减失败");
//        }
//        // 6、秒杀成功，创建对应的订单，并保存到数据库
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //当前线程的用户id
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        voucherOrder.setVoucherId(voucherId);
//        flag = this.save(voucherOrder);
//        if (!flag){
//            throw new RuntimeException("创建秒杀券订单失败");
//        }
//        // 返回订单id
//        return Result.ok(orderId+"弟弟，你抢到券了");
//    }
    /**
     * 秒杀优惠券  --乐观锁
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
//    @Override
//    @Transactional
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始，是否结束
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始!");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束!");
//        }
//        //3.判断库存是否充足
//        if(voucher.getStock()<=0){
//            return Result.fail("优惠券库存不足!");
//        }
//        //4.扣减库存
////        boolean success = seckillVoucherService.update()
////                .setSql("stock = stock -1")
////                .eq("voucher_id", voucherId).update();
//
//        //4.扣减库存,判断库存变化
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock -1")
//                .eq("voucher_id", voucherId).eq("stock",voucher.getStock())//where id = ? and stock =? 添加了乐观锁
//                .update();
//
    //4.扣减库存，改一下大于零就可以买，而不是看数据是否变动
//    boolean success = seckillVoucherService.update()
//            .setSql("stock = stock -1")
//            .eq("voucher_id", voucherId).gt("stock",0)//where id = ? and stock >0 添加了乐观锁
//            .update();
//        //5.创建订单
//        if(!success){
//            return Result.fail("优惠券库存不足!");
//        }
//        //6.返回订单id
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //6.1订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //6.2用户id
//        Long userId = UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
//        //6.3代金券id
//        voucherOrder.setVoucherId(voucherId);
//
//        //7.订单写入数据库
//        save(voucherOrder);
//
//        //8.返回订单Id
//        return Result.ok(orderId);
//    }
/**
 * 秒杀优惠券--redis实现分布式锁
 *
 * @param voucherId 券id
 * @return {@link Result}
 */
   /* @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //秒杀尚未开始
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //秒杀已经结束
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //仅限单体应用使用
//        synchronized (userId.toString().intern()) {
//            //实现获取代理对象 比较复杂 我采用了自己注入自己的方式
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return voucherOrderService.getResult(voucherId);
//        }
        //创建锁对象
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
//        boolean isLock = simpleRedisLock.tryLock(1200L);
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock){
            //获取失败,返回错误或者重试
            return Result.fail("一人一单哦！");
        }
        try {
            return voucherOrderService.getResult(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }
*/
    @Override
    @NotNull
    @Transactional(rollbackFor = Exception.class)
    public Result getResult(Long voucherId) {

        //加锁的原语代码
        //是否下单
        Long userId = UserHolder.getUser().getId();
        Long count = lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .count();
        if (count > 0) {
            return Result.fail("禁止重复购买");
        }
        //扣减库存
        boolean isSuccess = seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .eq(SeckillVoucher::getVoucherId, voucherId)
                        .gt(SeckillVoucher::getStock, 0)
                        .setSql("stock=stock-1"));
        if (!isSuccess) {
            //库存不足
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setId(orderId);
        this.save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }


}
