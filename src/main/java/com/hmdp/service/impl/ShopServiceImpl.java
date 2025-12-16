package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
//        缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(
//                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES
        );
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
//    public Shop queryWithMutex(Long id){
//        //1. 从redis查询商铺缓存
//        Shop shop = null;
//            String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//            //2. 判断是否存在
//            if (StrUtil.isNotBlank(shopJson)) {
//                //3 存在，返回
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            if (shopJson != null) {
//                return null;
//            }
//        try {
//            //四、实现缓存重建
//            //4.1 获取互斥锁
//            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
//            //4.2 判断是否获取成功
//            if(!isLock){
//                //4.3 失败，休眠重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //4.4 成功，根据id查询数据库
//            shop = getById(id);
//            //模拟重建延迟
//            Thread.sleep(200);
//            //5. 不存在，返回错误
//            if (shop == null) {
//                //将空值写入redis
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //6. 存在，写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //7. 释放互斥锁
//            unlock(LOCK_SHOP_KEY + id);
//        }
//        //8. 返回
//        return shop;
//    }

    @SneakyThrows
    public void saveShop2Redis(Long id, Long expireSeconds){
        //1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3. 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1. 更新数据库
        updateById(shop);
        //2. 删除redis
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}

