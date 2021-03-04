package org.apache.dubbo.demo.provider;

import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @ClassName: DemoServiceImpl2.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2021年03月01日
 */
public class DemoServiceImpl2 implements DemoService {
    private static final Logger logger = LoggerFactory.getLogger(DemoServiceImpl.class);

    @Override
    public String sayHello(String name) {
        logger.info("Hello " + name + ", request from consumer: " + RpcContext.getContext().getRemoteAddress());

        return "Hello " + name + ", response from provider: " + RpcContext.getContext().getLocalAddress();
    }

}
