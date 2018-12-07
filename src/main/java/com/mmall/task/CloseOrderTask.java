package com.mmall.task;

import com.mmall.common.Const;
import com.mmall.common.RedissonManager;
import com.mmall.service.IOrderService;
import com.mmall.util.PropertiesUtil;
import com.mmall.util.RedisShardedPoolUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CloseOrderTask {

    @Autowired
    private IOrderService iOrderService;
    //定时主要解决定时关单的需求，所采用的Spring Schedule的定时函数
    //存在的问题，分布式中多个服务器之间可能会产生线程死锁的问题
    @Autowired
    private RedissonManager redissonManager;
    @PreDestroy
    public void delLock(){
        RedisShardedPoolUtil.del(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
    }
//    @Scheduled(cron="0 */1 * * * ?")//每1分钟(每个1分钟的整数倍)
    public void closeOrderTaskV1(){
        log.info("关闭订单定时任务启动");
        int hour = Integer.parseInt(PropertiesUtil.getProperty("close.order.task.time.hour","2"));
//        iOrderService.closeOrder(hour);
        log.info("关闭订单定时任务结束");
    }
    //解决的方案是构建分布式锁
    //注意的技术点，分布式锁的实现，和双重分布式锁的实现，多进程和多线程debug的能力，并且要了解实现分布式锁的流程
    //后期还要使用Redisson框架进行二次封装，去更好实现可重入式锁的流程
    //todo
    @Scheduled(cron="0 */1 * * * ?")
    public void closeOrderTaskV2(){
        log.info("关闭订单定时任务启动");
        int lockTimeout = Integer.parseInt(PropertiesUtil.getProperty("lock.timeout.","5000"));
        Long setnxResult = RedisShardedPoolUtil.setnx(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK,String.valueOf(System.currentTimeMillis()+lockTimeout));
        if (setnxResult != null && setnxResult.intValue()==1){
            closeOrder(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
        }else{
            log.info("没有获得分布式锁:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
        }
//        iOrderService.closeOrder(hour);
        log.info("关闭订单定时任务结束");
    }
    private void closeOrder(String lockName){
        RedisShardedPoolUtil.expire(lockName,5);
        log.info("获取{},ThreadName:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK,Thread.currentThread().getName());
        int hour = Integer.parseInt(PropertiesUtil.getProperty("close.order.task.time.hour","2"));
        iOrderService.closeOrder(hour);
        RedisShardedPoolUtil.del(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
        log.info("获取{},ThreadName:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK,Thread.currentThread().getName());
        log.info("===============================================");
    }
//    @Scheduled(cron="0 */1 * * * ?")
    public void closeOrderTaskV3(){
        log.info("关闭订单定时任务启动");
        int lockTimeout = Integer.parseInt(PropertiesUtil.getProperty("lock.timeout.","5000"));
        Long setnxResult = RedisShardedPoolUtil.setnx(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK,String.valueOf(System.currentTimeMillis()+lockTimeout));
        if (setnxResult != null && setnxResult.intValue()==1){
            closeOrder(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
        }else{
            String lockValuesStr = RedisShardedPoolUtil.get(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
            if (lockValuesStr != null && System.currentTimeMillis()> Long.parseLong(lockValuesStr)){
               String getSetResult =  RedisShardedPoolUtil.getSet(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK,String.valueOf(System.currentTimeMillis()+lockTimeout));
               if (getSetResult == null || (getSetResult != null && StringUtils.equals(lockValuesStr,getSetResult))){
                   closeOrder(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);

               }else {
                   log.info("没有获得分布式锁:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
               }
            }else {
                log.info("没有获得分布式锁:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
            }
        }
        log.info("关闭订单定时任务结束");
    }
    @Scheduled(cron="0 */1 * * * ?")
    public void closeOrderTaskV4(){
        RLock lock = redissonManager.getRedisson().getLock(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
        boolean getLock = false;
        try {
            if(getLock = lock.tryLock(0,50, TimeUnit.SECONDS)){
                log.info("Redisson获取到分布式锁:{},ThreadName:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK,Thread.currentThread().getName());
                int hour = Integer.parseInt(PropertiesUtil.getProperty("close.order.task.time.hour","2"));
//                iOrderService.closeOrder(hour);
            }else{
                log.info("Redisson没有获取到分布式锁:{},ThreadName:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK,Thread.currentThread().getName());
            }
        } catch (InterruptedException e) {
            log.error("Redisson分布式锁获取异常",e);
        } finally {
            if(!getLock){
                return;
            }
            lock.unlock();
            log.info("Redisson分布式锁释放锁");
        }
    }
}
