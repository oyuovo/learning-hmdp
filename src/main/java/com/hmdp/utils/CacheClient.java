package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <R,ID>R queryWithPassThrough(
            String keyPrefix  ,ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        //1. 从redis查询商铺缓存
        String key=keyPrefix+id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if (StrUtil.isNotBlank(Json)) {
            //3 存在，返回
            return JSONUtil.toBean(Json, type);
        }
        if (Json != null) {
            return null;
        }
        //4.1 不存在，根据id查询数据库
        R  r = dbFallback.apply(id);
        //4.2 不存在，返回错误
        if (r == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5. 存在，写入redis
        this.set(key, r, time, unit);
        //6. 返回
        return r;
    }
    public <R,ID>R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        //1. 从redis查询商铺缓存
        String key=keyPrefix+id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if (StrUtil.isBlank(Json)) {
            return null;
        }
        //命中需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回店铺信息
            return r;
        }
        //已过期，需要缓存重建
        //获取互斥锁
        boolean isLock = tryLock(key, time, unit);
        if (isLock) {
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return r;
            }
            //获取成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //测试，时间测试很短
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(key);
                }
            });
            return r;
        }
        //获取失败
        return r;
    }
    private boolean tryLock(String key,Long time, TimeUnit unit){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "uid", time, unit);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
