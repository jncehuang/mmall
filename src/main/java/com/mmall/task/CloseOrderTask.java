package com.mmall.task;

import com.mmall.service.IOrderService;
import com.mmall.util.PropertiesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CloseOrderTask {

    @Autowired
    private IOrderService iOrderService;
    //定时主要解决定时关单的需求，所采用的Spring Schedule的定时函数
    //存在的问题，分布式中多个服务器之间可能会产生线程死锁的问题
    @Scheduled(cron="0 */1 * * * ?")//每1分钟(每个1分钟的整数倍)
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
}
