/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.protocol;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ListenableFilter;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;

import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_PROTOCOL;
import static org.apache.dubbo.rpc.Constants.REFERENCE_FILTER_KEY;
import static org.apache.dubbo.rpc.Constants.SERVICE_FILTER_KEY;

/**
 * ListenerProtocol
 */
public class ProtocolFilterWrapper implements Protocol {

    private final Protocol protocol;

    public ProtocolFilterWrapper(Protocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }


    //这里举的例子是服务端的，消费端和服务端都会调用这个方法。
    private static <T> Invoker<T> buildInvokerChain(final Invoker<T> invoker, String key, String group) {
        Invoker<T> last = invoker;
        // 根据url获取filter，根据url中的parameters取key为key的value所对应的filter，但是还会匹配group
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), key, group);

        // ConsumerContextFilter--->FutureFilter--->MonitorFilter
        // ConsumerContextFilter用来设置RpcContext
        //在这里把filter包装成Invoker对象，并且每个Invoker对象有三个个属性，一个是入参传进来的invoker变量(一般是JavassistProxyFactory中，getInvoker方法返回的AbstractProxyInvoker类型的匿名对象，是的，每个filter包装成Invoker对象其实都持有这个对象)，一个是filter，一个是next，filter就是下面遍历出来的每个filter，
        //next就是上一个filter包装成的Invoker对象。
        if (!filters.isEmpty()) {
            for (int i = filters.size() - 1; i >= 0; i--) {
                final Filter filter = filters.get(i);
                final Invoker<T> next = last;
                last = new Invoker<T>() {

                    @Override
                    public Class<T> getInterface() {
                        return invoker.getInterface();
                    }

                    @Override
                    public URL getUrl() {
                        return invoker.getUrl();
                    }

                    @Override
                    public boolean isAvailable() {
                        return invoker.isAvailable();
                    }

                    @Override
                    public Result invoke(Invocation invocation) throws RpcException {
                        Result asyncResult;
                        try {
                            // 得到一个异步结果
                            asyncResult = filter.invoke(next, invocation);
                        } catch (Exception e) {
                            // onError callback
                            if (filter instanceof ListenableFilter) {
                                Filter.Listener listener = ((ListenableFilter) filter).listener();
                                if (listener != null) {
                                    listener.onError(e, invoker, invocation);
                                }
                            }
                            throw e;
                        }
                        return asyncResult;
                    }

                    @Override
                    public void destroy() {
                        invoker.destroy();
                    }

                    @Override
                    public String toString() {
                        return invoker.toString();
                    }
                };
            }
        }
        //最终这个last对象是EchoFilter所包装成的Invoker，最终形成的单向链表是，Invoker(EchoFilter)的next指向------>..........------>Invoker(ExceptionFilter)的next指向------->AbstractProxyInvoker这个匿名对象,这里举的例子是服务端的，消费端和服务端都会调用这个方法。
        return new CallbackRegistrationInvoker<>(last, filters);
    }

    @Override
    public int getDefaultPort() {
        return protocol.getDefaultPort();
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        if (REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
            return protocol.export(invoker);
        }
        return protocol.export(buildInvokerChain(invoker, SERVICE_FILTER_KEY, CommonConstants.PROVIDER));
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        if (REGISTRY_PROTOCOL.equals(url.getProtocol())) {//如果这个url信息表示的是注册中心的url
            return protocol.refer(type, url);
        }
        return buildInvokerChain(protocol.refer(type, url), REFERENCE_FILTER_KEY, CommonConstants.CONSUMER);
    }

    @Override
    public void destroy() {
        protocol.destroy();
    }

    /**
     * Register callback for each filter may be better, just like {@link java.util.concurrent.CompletionStage}, each callback
     * registration generates a new CompletionStage whose status is determined by the original CompletionStage.
     *
     * If bridging status between filters is proved to not has significant performance drop, consider revert to the following commit:
     * https://github.com/apache/dubbo/pull/4127
     */
    static class CallbackRegistrationInvoker<T> implements Invoker<T> {

        private final Invoker<T> filterInvoker;
        private final List<Filter> filters;

        public CallbackRegistrationInvoker(Invoker<T> filterInvoker, List<Filter> filters) {
            this.filterInvoker = filterInvoker;
            this.filters = filters;
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            // 执行过滤器链
            Result asyncResult = filterInvoker.invoke(invocation);

            // 过滤器都执行完了之后，回调每个过滤器的onResponse或onError方法
            asyncResult = asyncResult.whenCompleteWithContext((r, t) -> {
                for (int i = filters.size() - 1; i >= 0; i--) {
                    Filter filter = filters.get(i);
                    // onResponse callback
                    if (filter instanceof ListenableFilter) {
                        Filter.Listener listener = ((ListenableFilter) filter).listener();
                        if (listener != null) {
                            if (t == null) {
                                listener.onResponse(r, filterInvoker, invocation);
                            } else {
                                listener.onError(t, filterInvoker, invocation);
                            }
                        }
                    } else {
                        filter.onResponse(r, filterInvoker, invocation);
                    }
                }
            });
            return asyncResult;
        }

        @Override
        public Class<T> getInterface() {
            return filterInvoker.getInterface();
        }

        @Override
        public URL getUrl() {
            return filterInvoker.getUrl();
        }

        @Override
        public boolean isAvailable() {
            return filterInvoker.isAvailable();
        }

        @Override
        public void destroy() {
            filterInvoker.destroy();
        }
    }
}
