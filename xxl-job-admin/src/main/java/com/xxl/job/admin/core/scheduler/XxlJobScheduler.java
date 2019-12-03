package com.xxl.job.admin.core.scheduler;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.thread.*;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.xxl.rpc.remoting.invoker.call.CallType;
import com.xxl.rpc.remoting.invoker.reference.XxlRpcReferenceBean;
import com.xxl.rpc.remoting.invoker.route.LoadBalance;
import com.xxl.rpc.remoting.net.impl.netty_http.client.NettyHttpClient;
import com.xxl.rpc.serialize.impl.HessianSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author xuxueli 2018-10-28 00:18:17
 */

public class XxlJobScheduler  {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobScheduler.class);


    public void init() throws Exception {
        //国际化
        initI18n();

        //调度中心注册守护线程（一直守护着执行器的注册，更新执行器地址列表）
        JobRegistryMonitorHelper.getInstance().start();

        //任务失败处理的守护线程
        JobFailMonitorHelper.getInstance().start();

        //初始化任务调度线程池（快、慢）
        JobTriggerPoolHelper.toStart();

        //报表统计及历史日志删除守护线程
        JobLogReportHelper.getInstance().start();

        //预读线程和调度线程
        JobScheduleHelper.getInstance().start();

        logger.info(">>>>>>>>> init xxl-job admin success.");
    }

    
    public void destroy() throws Exception {

        // stop-schedule
        JobScheduleHelper.getInstance().toStop();

        // admin log report stop
        JobLogReportHelper.getInstance().toStop();

        // admin trigger pool stop
        JobTriggerPoolHelper.toStop();

        // admin monitor stop
        JobFailMonitorHelper.getInstance().toStop();

        // admin registry stop
        JobRegistryMonitorHelper.getInstance().toStop();

    }

    // ---------------------- I18n ----------------------

    private void initI18n(){
        for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {
            item.setTitle(I18nUtil.getString("jobconf_block_".concat(item.name())));
        }
    }

    // ---------------------- executor-client ----------------------
    private static ConcurrentMap<String, ExecutorBiz> executorBizRepository = new ConcurrentHashMap<String, ExecutorBiz>();
    public static ExecutorBiz getExecutorBiz(String address) throws Exception {
        //检验地址
        if (address==null || address.trim().length()==0) {
            return null;
        }

        //从缓存拿出次地址的ExecutorBiz
        address = address.trim();
        ExecutorBiz executorBiz = executorBizRepository.get(address);
        if (executorBiz != null) {
            return executorBiz;
        }

        //创建XxlRpcReferenceBean
        XxlRpcReferenceBean referenceBean = new XxlRpcReferenceBean();
        referenceBean.setClient(NettyHttpClient.class);
        referenceBean.setSerializer(HessianSerializer.class);
        referenceBean.setCallType(CallType.SYNC);
        referenceBean.setLoadBalance(LoadBalance.ROUND);
        referenceBean.setIface(ExecutorBiz.class);
        referenceBean.setVersion(null);
        referenceBean.setTimeout(3000);
        referenceBean.setAddress(address);
        referenceBean.setAccessToken(XxlJobAdminConfig.getAdminConfig().getAccessToken());
        referenceBean.setInvokeCallback(null);
        referenceBean.setInvokerFactory(null);

        //执行器注册的xxlRpcProvider的类就是ExecutorBiz，这是admin和执行器直接通过xxlRpc通信的逻辑类
        executorBiz = (ExecutorBiz) referenceBean.getObject();
        //放入缓存
        executorBizRepository.put(address, executorBiz);
        return executorBiz;
    }

}
