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
package org.apache.dubbo.registry.integration;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.configcenter.DynamicConfiguration;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.registry.support.ProviderConsumerRegTable;
import org.apache.dubbo.registry.support.ProviderInvokerWrapper;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Configurator;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.protocol.InvokerWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CLUSTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.HIDE_KEY_PREFIX;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.RELEASE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMESTAMP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.FilterConstants.VALIDATION_KEY;
import static org.apache.dubbo.common.constants.QosConstants.ACCEPT_FOREIGN_IP;
import static org.apache.dubbo.common.constants.QosConstants.QOS_ENABLE;
import static org.apache.dubbo.common.constants.QosConstants.QOS_HOST;
import static org.apache.dubbo.common.constants.QosConstants.QOS_PORT;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONSUMERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.OVERRIDE_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTERS_CATEGORY;
import static org.apache.dubbo.common.utils.UrlUtils.classifyUrls;
import static org.apache.dubbo.registry.Constants.CONFIGURATORS_SUFFIX;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.DEFAULT_REGISTRY;
import static org.apache.dubbo.registry.Constants.EXTRA_KEYS_KEY;
import static org.apache.dubbo.registry.Constants.PROVIDER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTER_IP_KEY;
import static org.apache.dubbo.registry.Constants.REGISTER_KEY;
import static org.apache.dubbo.registry.Constants.SIMPLIFIED_KEY;
import static org.apache.dubbo.remoting.Constants.BIND_IP_KEY;
import static org.apache.dubbo.remoting.Constants.BIND_PORT_KEY;
import static org.apache.dubbo.remoting.Constants.CHECK_KEY;
import static org.apache.dubbo.remoting.Constants.CODEC_KEY;
import static org.apache.dubbo.remoting.Constants.CONNECTIONS_KEY;
import static org.apache.dubbo.remoting.Constants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.remoting.Constants.EXCHANGER_KEY;
import static org.apache.dubbo.remoting.Constants.SERIALIZATION_KEY;
import static org.apache.dubbo.rpc.Constants.DEPRECATED_KEY;
import static org.apache.dubbo.rpc.Constants.INTERFACES;
import static org.apache.dubbo.rpc.Constants.MOCK_KEY;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.LOADBALANCE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WARMUP_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WEIGHT_KEY;

/**
 * RegistryProtocol
 */
public class RegistryProtocol implements Protocol {
    public static final String[] DEFAULT_REGISTER_PROVIDER_KEYS = {
            APPLICATION_KEY, CODEC_KEY, EXCHANGER_KEY, SERIALIZATION_KEY, CLUSTER_KEY, CONNECTIONS_KEY, DEPRECATED_KEY,
            GROUP_KEY, LOADBALANCE_KEY, MOCK_KEY, PATH_KEY, TIMEOUT_KEY, TOKEN_KEY, VERSION_KEY, WARMUP_KEY,
            WEIGHT_KEY, TIMESTAMP_KEY, DUBBO_VERSION_KEY, RELEASE_KEY
    };

    public static final String[] DEFAULT_REGISTER_CONSUMER_KEYS = {
            APPLICATION_KEY, VERSION_KEY, GROUP_KEY, DUBBO_VERSION_KEY, RELEASE_KEY
    };

    private final static Logger logger = LoggerFactory.getLogger(RegistryProtocol.class);
    private static RegistryProtocol INSTANCE;
    private final Map<URL, NotifyListener> overrideListeners = new ConcurrentHashMap<>();
    private final Map<String, ServiceConfigurationListener> serviceConfigurationListeners = new ConcurrentHashMap<>();
    private final ProviderConfigurationListener providerConfigurationListener = new ProviderConfigurationListener();
    //To solve the problem of RMI repeated exposure port conflicts, the services that have been exposed are no longer exposed.
    //providerurl <--> exporter
    private final ConcurrentMap<String, ExporterChangeableWrapper<?>> bounds = new ConcurrentHashMap<>();
    private Cluster cluster;
    private Protocol protocol;
    private RegistryFactory registryFactory;
    private ProxyFactory proxyFactory;

    public RegistryProtocol() {
        INSTANCE = this;
    }

    public static RegistryProtocol getRegistryProtocol() {
        if (INSTANCE == null) {
            ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(REGISTRY_PROTOCOL); // load
        }
        return INSTANCE;
    }

    //Filter the parameters that do not need to be output in url(Starting with .)
    private static String[] getFilteredKeys(URL url) {
        Map<String, String> params = url.getParameters();

        // 过滤url的参数，找到startsWith(HIDE_KEY_PREFIX)的key
        if (CollectionUtils.isNotEmptyMap(params)) {
            return params.keySet().stream()
                    .filter(k -> k.startsWith(HIDE_KEY_PREFIX))
                    .toArray(String[]::new);
        } else {
            return new String[0];
        }
    }

    // 依赖注入，注入进来的是Cluster的Adaptive类
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public void setRegistryFactory(RegistryFactory registryFactory) {
        this.registryFactory = registryFactory;
    }

    public void setProxyFactory(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    public int getDefaultPort() {
        return 9090;
    }

    public Map<URL, NotifyListener> getOverrideListeners() {
        return overrideListeners;
    }

    public void register(URL registryUrl, URL registeredProviderUrl) {
        //同样的，得到org.apache.dubbo.registry.ListenerRegistryWrapper对象，里面包装着org.apache.dubbo.registry.nacos.NacosRegistry对象
        Registry registry = registryFactory.getRegistry(registryUrl);
        // 调用org.apache.dubbo.registry.support.FailbackRegistry.register方法，最终调用到org.apache.dubbo.registry.support.FailbackRegistry.register方法啊，将服务注册到nacso中去
        registry.register(registeredProviderUrl);
    }

    public void unregister(URL registryUrl, URL registeredProviderUrl) {
        Registry registry = registryFactory.getRegistry(registryUrl);
        registry.unregister(registeredProviderUrl);
    }

    @Override
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        //入参就是org.apache.dubbo.config.invoker.DelegateProviderMetaDataInvoker，我们在DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);这行代码中包装的
        // 导出服务
        // registry://   ---> RegistryProtocol
        // zookeeper://  ---> ZookeeperRegistry
        // dubbo://      ---> DubboProtocol
        // provider://   --->

        //把originInvoker里面的注册中心的地址registry://192.168.0.188:8848/org.apache.dubbo.registry.RegistryService?application=dubbo-provider-demo&dubbo=2.0.2&export=dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=15796&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649584872140&version=async&pid=15796&qos.enable=false&registry=nacos&release=2.7.5&timestamp=1649584830218
        //转换为
        //nacos://192.168.0.188:8848/org.apache.dubbo.registry.RegistryService?application=dubbo-provider-demo&dubbo=2.0.2&export=dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=15796&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649584872140&version=async&pid=15796&qos.enable=false&release=2.7.5&timestamp=1649584830218

        URL registryUrl = getRegistryUrl(originInvoker);
        // 得到服务提供者url
        //dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=15796&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649584872140&version=async
        URL providerUrl = getProviderUrl(originInvoker);

        // Subscribe the override data
        // FIXME When the provider subscribes, it will affect the scene : a certain JVM exposes the service and call
        //  the same service. Because the subscribed is cached key with the name of the service, it causes the
        //  subscription information to cover.

        //将服务提供者的url改为provider://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&category=configurators&check=false&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=15796&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649584872140&version=async
        // overrideSubscribeUrl是老版本的动态配置监听url，表示了需要监听的服务以及监听的类型（configurators， 这是老版本上的动态配置）
        // 在providerUrl的基础上，生成一个overrideSubscribeUrl，协议为provider://，增加参数category=configurators&check=false
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);

        // 一个overrideSubscribeUrl对应一个OverrideListener，用来监听变化事件，监听到overrideSubscribeUrl的变化后，
        // OverrideListener就会根据变化进行相应处理，具体处理逻辑看OverrideListener的实现
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);
        // 在这个方法里会利用providerConfigurationListener和serviceConfigurationListener去重写providerUrl
        // providerConfigurationListener表示应用级别的动态配置监听器，providerConfigurationListener是RegistyProtocol的一个属性
        // serviceConfigurationListener表示服务级别的动态配置监听器，serviceConfigurationListener是在每暴露一个服务时就会生成一个
        // 这两个监听器都是新版本中的监听器
        // 新版本监听的zk路径是：
        // 服务： /dubbo/config/dubbo/org.apache.dubbo.demo.DemoService.configurators节点的内容
        // 应用： /dubbo/config/dubbo/dubbo-demo-provider-application.configurators节点的内容
        // 注意，要喝配置中心的路径区分开来，配置中心的路径是：
        // 应用：/dubbo/config/dubbo/org.apache.dubbo.demo.DemoService/dubbo.properties节点的内容
        // 全局：/dubbo/config/dubbo/dubbo.properties节点的内容
        //shihytodo 对于nacos，这个providerUrl没什么变化
        providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);

        // export invoker
        // 这里就只是往bounds这个对象里里面记录了originInvoker，这bounds就是为了重新导入的时候用的，在这里会进行服务导出，所谓导出就是记录export，然后启动netty服务器
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);

        // url to registry
        //得到org.apache.dubbo.registry.ListenerRegistryWrapper对象，里面包装着org.apache.dubbo.registry.nacos.NacosRegistry对象
        final Registry registry = getRegistry(originInvoker);

        // 得到真正要存入到注册中心去的providerUrl,会对服务提供者url中的参数进行简化，移除和具体服务无关的一些参数
        //值为dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=11176&release=2.7.5&revision=async&side=provider&timestamp=1649603405541&version=async
        final URL registeredProviderUrl = getRegisteredProviderUrl(providerUrl, registryUrl);

        // 将当前服务提供者Invoker，以及该服务对应的注册中心地址，以及简化后的服务url存入ProviderConsumerRegTable
        ProviderInvokerWrapper<T> providerInvokerWrapper = ProviderConsumerRegTable.registerProvider(originInvoker,
                registryUrl, registeredProviderUrl);


        //to judge if we need to delay publish
        //是否需要注册到注册中心，默认是ture
        boolean register = providerUrl.getParameter(REGISTER_KEY, true);
        if (register) {
            // 注册服务，把简化后的服务提供者url注册到nacos中取
            register(registryUrl, registeredProviderUrl);
            providerInvokerWrapper.setReg(true);
        }

        // 针对老版本的动态配置，需要把overrideSubscribeListener绑定到overrideSubscribeUrl上去进行监听
        // Deprecated! Subscribe to override rules in 2.6.x or before.
        // 老版本监听的zk路径是：/dubbo/org.apache.dubbo.demo.DemoService/configurators/override://0.0.0.0/org.apache.dubbo.demo.DemoService?category=configurators&compatible_config=true&dynamic=false&enabled=true&timeout=6000
        // 监听的是路径的内容，不是节点的内容
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);


        exporter.setRegisterUrl(registeredProviderUrl);
        exporter.setSubscribeUrl(overrideSubscribeUrl);
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<>(exporter);
    }

    private URL overrideUrlWithConfig(URL providerUrl, OverrideListener listener) {
        // 应用配置，providerConfigurationListener是在属性那里直接初始化好的，providerConfigurationListener会监听配置中心的应用配置信息变动
        providerUrl = providerConfigurationListener.overrideUrl(providerUrl);

        // 服务配置，new ServiceConfigurationListener的时候回初始化，ServiceConfigurationListener会监听配置中心的服务信息配置信息变动
        ServiceConfigurationListener serviceConfigurationListener = new ServiceConfigurationListener(providerUrl, listener);
        serviceConfigurationListeners.put(providerUrl.getServiceKey(), serviceConfigurationListener);
        return serviceConfigurationListener.overrideUrl(providerUrl);
    }

    @SuppressWarnings("unchecked")
    private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker, URL providerUrl) {
        //key就是providerUrl这个url去掉dynamic和enabled后的toString形式
        //原url是dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=25296&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649588667957&version=async
        //之后是dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&deprecated=false&dubbo=2.0.2&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=25296&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649588667957&version=async
        //少了dynamic这个参数
        String key = getCacheKey(originInvoker);
        //然后放入bounds中，这bounds就是为了重新导入的时候用的。这个protocol变量的值，其实就是和Exporter<?> exporter = protocol.export(wrapperInvoker);这句代码的protocol是同一个对象，wrapperInvoker和originInvoker也是同一个对象
        return (ExporterChangeableWrapper<T>) bounds.computeIfAbsent(key, s -> {
            Invoker<?> invokerDelegate = new InvokerDelegate<>(originInvoker, providerUrl);
            // protocol属性的值是哪来的，是在SPI中注入进来的，是一个代理类
            // 这里实际利用的就是DubboProtocol或HttpProtocol去export  NettyServer
            // 为什么需要ExporterChangeableWrapper？方便注销已经被导出的服务
            return new ExporterChangeableWrapper<>((Exporter<T>) protocol.export(invokerDelegate), originInvoker);
        });
    }

    public <T> void reExport(final Invoker<T> originInvoker, URL newInvokerUrl) {

        // 根据newInvokerUrl进行导出
        // update local exporter
        ExporterChangeableWrapper exporter = doChangeLocalExport(originInvoker, newInvokerUrl);

        // 获取准确的ProviderUrl
        // update registry
        URL registryUrl = getRegistryUrl(originInvoker);
        // 对于一个服务提供者url，在注册到注册中心时，会先进行简化，所以如果
        final URL registeredProviderUrl = getRegisteredProviderUrl(newInvokerUrl, registryUrl);

        //decide if we need to re-publish
        // 根据getServiceKey获取ProviderInvokerWrapper
        ProviderInvokerWrapper<T> providerInvokerWrapper = ProviderConsumerRegTable.getProviderWrapper(registeredProviderUrl, originInvoker);
        // 生成一个新的ProviderInvokerWrapper
        ProviderInvokerWrapper<T> newProviderInvokerWrapper = ProviderConsumerRegTable.registerProvider(originInvoker, registryUrl, registeredProviderUrl);

        /**
         * Only if the new url going to Registry is different with the previous one should we do unregister and register.
         * 如果新的服务提供者url简化后的url和这个服务之前的服务提供者url简化后的url不相等，则需要把新的简化后的服务提供者url注册到注册中心去
         */
        if (providerInvokerWrapper.isReg() && !registeredProviderUrl.equals(providerInvokerWrapper.getProviderUrl())) {
            unregister(registryUrl, providerInvokerWrapper.getProviderUrl());
            register(registryUrl, registeredProviderUrl);
            newProviderInvokerWrapper.setReg(true);
        }

        exporter.setRegisterUrl(registeredProviderUrl);
    }

    /**
     * Reexport the invoker of the modified url
     *
     * @param originInvoker
     * @param newInvokerUrl
     */
    @SuppressWarnings("unchecked")
    private <T> ExporterChangeableWrapper doChangeLocalExport(final Invoker<T> originInvoker, URL newInvokerUrl) {
        String key = getCacheKey(originInvoker);
        final ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
        if (exporter == null) {
            logger.warn(new IllegalStateException("error state, exporter should not be null"));
        } else {
            // 到这里才能真正明白，为什么需要InvokerDelegate
            // InvokerDelegate表示一个调用者，由invoker+url构成，invoker不变，url可变
            final Invoker<T> invokerDelegate = new InvokerDelegate<T>(originInvoker, newInvokerUrl);
            exporter.setExporter(protocol.export(invokerDelegate));
        }
        return exporter;
    }

    /**
     * Get an instance of registry based on the address of invoker
     *
     * @param originInvoker
     * @return
     */
    private Registry getRegistry(final Invoker<?> originInvoker) {
        //得到由registry://192.168.0.188:8848/org.apache.dubbo.registry.RegistryService?application=dubbo-provider-demo&dubbo=2.0.2&export=dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=11176&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649603405541&version=async&pid=11176&qos.enable=false&registry=nacos&release=2.7.5&timestamp=1649603311504，
        // 转换后的具体的注册中心的url，
        // nacos://192.168.0.188:8848/org.apache.dubbo.registry.RegistryService?application=dubbo-provider-demo&dubbo=2.0.2&export=dubbo://192.168.0.105:20881/com.tuling.DemoService?anyhost=true&application=dubbo-provider-demo&bind.ip=192.168.0.105&bind.port=20881&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.tuling.DemoService&methods=sayHello,sayHelloAsync&pid=11176&qos.enable=false&release=2.7.5&revision=async&side=provider&timestamp=1649603405541&version=async&pid=11176&qos.enable=false&release=2.7.5&timestamp=1649603311504
        URL registryUrl = getRegistryUrl(originInvoker);
        //这里用了spi，对调到org.apache.dubbo.registry.support.AbstractRegistryFactory.getRegistry(org.apache.dubbo.common.URL)方法
        return registryFactory.getRegistry(registryUrl);
    }

    private URL getRegistryUrl(Invoker<?> originInvoker) {
        // 将registry://xxx?xx=xx&registry=zookeeper 转为 zookeeper://xxx?xx=xx

        URL registryUrl = originInvoker.getUrl();
        if (REGISTRY_PROTOCOL.equals(registryUrl.getProtocol())) {
            String protocol = registryUrl.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY);
            registryUrl = registryUrl.setProtocol(protocol).removeParameter(REGISTRY_KEY);
        }
        return registryUrl;
    }


    /**
     * Return the url that is registered to the registry and filter the url parameter once
     * 得到能存入到注册中心去的providerUrl
     * @param providerUrl
     * @return url to registry.
     */
    private URL getRegisteredProviderUrl(final URL providerUrl, final URL registryUrl) {
        //The address you see at the registry
        // 默认都是走这里
        if (!registryUrl.getParameter(SIMPLIFIED_KEY, false)) {
            // 移除key以"."开始的参数，以及其他跟服务本身没有关系的参数，比如监控，绑定ip，qosd等等
            return providerUrl.removeParameters(getFilteredKeys(providerUrl)).removeParameters(
                    MONITOR_KEY, BIND_IP_KEY, BIND_PORT_KEY, QOS_ENABLE, QOS_HOST, QOS_PORT, ACCEPT_FOREIGN_IP, VALIDATION_KEY,
                    INTERFACES);
        } else {
            String extraKeys = registryUrl.getParameter(EXTRA_KEYS_KEY, "");
            // if path is not the same as interface name then we should keep INTERFACE_KEY,
            // otherwise, the registry structure of zookeeper would be '/dubbo/path/providers',
            // but what we expect is '/dubbo/interface/providers'
            // 如果path不等于interface的值，那么则把INTERFACE_KEY添加到extraKeys中
            if (!providerUrl.getPath().equals(providerUrl.getParameter(INTERFACE_KEY))) {
                if (StringUtils.isNotEmpty(extraKeys)) {
                    extraKeys += ",";
                }
                extraKeys += INTERFACE_KEY;
            }

            // paramsToRegistry包括了DEFAULT_REGISTER_PROVIDER_KEYS和extraKeys
            String[] paramsToRegistry = getParamsToRegistry(DEFAULT_REGISTER_PROVIDER_KEYS
                    , COMMA_SPLIT_PATTERN.split(extraKeys));

            // 生成只含有paramsToRegistry的对应的参数，并且该参数不能为空
            return URL.valueOf(providerUrl, paramsToRegistry, providerUrl.getParameter(METHODS_KEY, (String[]) null));
        }

    }

    private URL getSubscribedOverrideUrl(URL registeredProviderUrl) {
        // 注意，setProtocol方法会重新new一个url
        return registeredProviderUrl.setProtocol(PROVIDER_PROTOCOL)
                .addParameters(CATEGORY_KEY, CONFIGURATORS_CATEGORY, CHECK_KEY, String.valueOf(false));
    }

    /**
     * Get the address of the providerUrl through the url of the invoker
     *
     * @param originInvoker
     * @return
     */
    private URL getProviderUrl(final Invoker<?> originInvoker) {
        String export = originInvoker.getUrl().getParameterAndDecoded(EXPORT_KEY);
        if (export == null || export.length() == 0) {
            throw new IllegalArgumentException("The registry export url is null! registry: " + originInvoker.getUrl());
        }
        return URL.valueOf(export);
    }

    /**
     * Get the key cached in bounds by invoker
     *
     * @param originInvoker
     * @return
     */
    private String getCacheKey(final Invoker<?> originInvoker) {
        URL providerUrl = getProviderUrl(originInvoker);
        String key = providerUrl.removeParameters("dynamic", "enabled").toFullString();
        return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {

        // 从registry://的url中获取对应的注册中心，比如zookeeper， 默认为dubbo，dubbo提供了自带的注册中心实现
        // url由 registry:// 改变为---> zookeeper://
        url = URLBuilder.from(url)
                .setProtocol(url.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY))//从parameters属性中获取key为registry的值，如果用的是zk，这个值为zookeeper
                .removeParameter(REGISTRY_KEY)//从parameters属性中删除key为registry的值
                .build();

        // 拿到注册中心实现，ZookeeperRegistry
        Registry registry = registryFactory.getRegistry(url);

        // 下面这个代码，通过过git历史提交记录是用来解决SimpleRegistry不可用的问题
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        // qs表示 queryString, 表示url中的参数，表示@Reference注解中消费者引入服务时所配置的参数，
        //url的parameters属性中key为refer的属性的值为application%3Ddubbo-demo-consumer-application%26dubbo%3D2.0.2%26interface%3Dorg.apache.dubbo.demo.DemoService%26lazy%3Dfalse%26methods%3DsayHello%26pid%3D9116%26register.ip%3D192.168.3.2%26release%3D2.7.0%26side%3Dconsumer%26sticky%3Dfalse%26timeout%3D30000%26timestamp%3D1615367569601
        //getParameterAndDecoded这个方法，获取这个refer这个key对应的value，并解码成map
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));

        // group="a,b" or group="*"
        //看@Reference注解中是否配置了group这个属性的值
        String group = qs.get(GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((COMMA_SPLIT_PATTERN.split(group)).length > 1 || "*".equals(group)) {
                // group有多个值，这里的cluster为MergeableCluster
                return doRefer(getMergeableCluster(), registry, type, url);
            }
        }

        // 这里的cluster是cluster的Adaptive对象,结构是，dubbo帮我们生成的代理对象---->MockClusterWrapper---->FailoverCluster
        //
        return doRefer(cluster, registry, type, url);
    }

    private Cluster getMergeableCluster() {
        return ExtensionLoader.getExtensionLoader(Cluster.class).getExtension("mergeable");
    }

    /**
     *
     * @param cluster
     * @param registry  注册中心实现类，ZookeeperRegistry
     * @param type  服务接口类
     * @param url   注册中心url
     * @param <T>
     * @return
     */
    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        // RegistryDirectory表示动态服务目录，会和注册中心的数据保持同步
        // type表示一个服务对应一个RegistryDirectory，url表示注册中心地址
        // 在消费端，最核心的就是RegistryDirectory
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);


        // all attributes of REFER_KEY
        // 引入服务所配置的参数
        Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());

        // 消费者url,大概结构是consumer://192.168.3.2/org.apache.dubbo.demo.DemoService?application=dubbo-demo-consumer-application&dubbo=2.0.2&interface=org.apache.dubbo.demo.DemoService&lazy=false&methods=sayHello&pid=9580&release=2.7.0&side=consumer&sticky=false&timeout=30000&timestamp=1615442032599
        URL subscribeUrl = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (!ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(REGISTER_KEY, true)) {
            directory.setRegisteredConsumerUrl(getRegisteredConsumerUrl(subscribeUrl, url));

            // 向注册中心注册简化后的消费url
            registry.register(directory.getRegisteredConsumerUrl());
        }

        // 构造路由链,路由链会在引入服务时按路由条件进行过滤
        // 路由链是动态服务目录中的一个属性，通过路由链可以过滤某些服务提供者
        directory.buildRouterChain(subscribeUrl);

        // 服务目录需要订阅的几个路径，即消费者需要订阅的几个目录，在这里回去注册中心拉去服务提供者的数据
        // 当前所引入的服务的消费应用目录：/dubbo/config/dubbo/dubbo-demo-consumer-application.configurators
        // 当前所引入的服务的动态配置目录：/dubbo/config/dubbo/org.apache.dubbo.demo.DemoService:1.1.1:g1.configurators
        // 当前所引入的服务的提供者目录：/dubbo/org.apache.dubbo.demo.DemoService/providers
        // 当前所引入的服务的老版本动态配置目录：/dubbo/org.apache.dubbo.demo.DemoService/configurators
        // 当前所引入的服务的老版本路由器目录：/dubbo/org.apache.dubbo.demo.DemoService/routers
        directory.subscribe(subscribeUrl.addParameter(CATEGORY_KEY,
                PROVIDERS_CATEGORY + "," + CONFIGURATORS_CATEGORY + "," + ROUTERS_CATEGORY));

        // 利用传进来的cluster，join得到invoker,这个cluster的结构是，dubbo的代理对象--->MockClusterWrapper----->FailoverCluster
        //这个invoker的结构是，MockClusterInvoker---->FailoverClusterInvoker,这两个invoker都包含了directory对象，directory对象里面包含了过滤器，和DubboInvoker
        //这个cluster是个adaptive类型的，它是个dubbo代理对象，它的逻辑中有段代码，
        //org.apache.dubbo.common.URL url = directory.getUrl();//directory就是形参的directory
        //String extName = url.getParameter("cluster", "failover");//这里默认是key为cluster的值为空，所以，默认取值为failover
        //org.apache.dubbo.rpc.cluster.Cluster extension = (org.apache.dubbo.rpc.cluster.Cluster)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.cluster.Cluster.class).getExtension(extName);所以会去调用扩展点名称为failover的Cluster接口的实现类，即org.apache.dubbo.rpc.cluster.support.FailoverCluster的join方法
        Invoker invoker = cluster.join(directory);
        ProviderConsumerRegTable.registerConsumer(invoker, url, subscribeUrl, directory);
        return invoker;
    }

    public URL getRegisteredConsumerUrl(final URL consumerUrl, URL registryUrl) {
        if (!registryUrl.getParameter(SIMPLIFIED_KEY, false)) {
            return consumerUrl.addParameters(CATEGORY_KEY, CONSUMERS_CATEGORY,
                    CHECK_KEY, String.valueOf(false));
        } else {
            return URL.valueOf(consumerUrl, DEFAULT_REGISTER_CONSUMER_KEYS, null).addParameters(
                    CATEGORY_KEY, CONSUMERS_CATEGORY, CHECK_KEY, String.valueOf(false));
        }
    }

    // available to test
    public String[] getParamsToRegistry(String[] defaultKeys, String[] additionalParameterKeys) {
        int additionalLen = additionalParameterKeys.length;
        String[] registryParams = new String[defaultKeys.length + additionalLen];
        // 把defaultKeys复制到registryParams中
        System.arraycopy(defaultKeys, 0, registryParams, 0, defaultKeys.length);
        // 把additionalParameterKeys复制到registryParams中
        System.arraycopy(additionalParameterKeys, 0, registryParams, defaultKeys.length, additionalLen);
        // registryParams包括了defaultKeys和additionalParameterKeys
        return registryParams;
    }

    @Override
    public void destroy() {
        List<Exporter<?>> exporters = new ArrayList<Exporter<?>>(bounds.values());
        for (Exporter<?> exporter : exporters) {
            exporter.unexport();
        }
        bounds.clear();

        DynamicConfiguration.getDynamicConfiguration()
                .removeListener(ApplicationModel.getApplication() + CONFIGURATORS_SUFFIX, providerConfigurationListener);
    }

    //Merge the urls of configurators
    private static URL getConfigedInvokerUrl(List<Configurator> configurators, URL url) {
        if (configurators != null && configurators.size() > 0) {
            for (Configurator configurator : configurators) {
                url = configurator.configure(url);
            }
        }
        return url;
    }

    public static class InvokerDelegate<T> extends InvokerWrapper<T> {
        private final Invoker<T> invoker;

        /**
         * @param invoker
         * @param url     invoker.getUrl return this value
         */
        public InvokerDelegate(Invoker<T> invoker, URL url) {
            super(invoker, url);
            this.invoker = invoker;
        }

        public Invoker<T> getInvoker() {
            if (invoker instanceof InvokerDelegate) {
                return ((InvokerDelegate<T>) invoker).getInvoker();
            } else {
                return invoker;
            }
        }
    }

    private static class DestroyableExporter<T> implements Exporter<T> {

        private Exporter<T> exporter;

        public DestroyableExporter(Exporter<T> exporter) {
            this.exporter = exporter;
        }

        @Override
        public Invoker<T> getInvoker() {
            return exporter.getInvoker();
        }

        @Override
        public void unexport() {
            exporter.unexport();
        }
    }

    /**
     * Reexport: the exporter destroy problem in protocol
     * 1.Ensure that the exporter returned by registryprotocol can be normal destroyed
     * 2.No need to re-register to the registry after notify
     * 3.The invoker passed by the export method , would better to be the invoker of exporter
     */
    // 当subscribeUrl对应的数据发生了改变，OverrideListener将收到通知
    private class OverrideListener implements NotifyListener {
        private final URL subscribeUrl;
        private final Invoker originInvoker;


        private List<Configurator> configurators;

        public OverrideListener(URL subscribeUrl, Invoker originalInvoker) {
            this.subscribeUrl = subscribeUrl;
            this.originInvoker = originalInvoker;
        }

        /**
         * @param urls The list of registered information, is always not empty, The meaning is the same as the
         *             return value of {@link org.apache.dubbo.registry.RegistryService#lookup(URL)}.
         */
        @Override
        public synchronized void notify(List<URL> urls) {
            logger.debug("original override urls: " + urls);

            List<URL> matchedUrls = getMatchedUrls(urls, subscribeUrl.addParameter(CATEGORY_KEY,
                    CONFIGURATORS_CATEGORY));
            logger.debug("subscribe url: " + subscribeUrl + ", override urls: " + matchedUrls);

            // No matching results
            if (matchedUrls.isEmpty()) {
                return;
            }

            // 对发生了变化的url进行过滤，只取url是override协议，或者参数category等于configurators的url
            this.configurators = Configurator.toConfigurators(classifyUrls(matchedUrls, UrlUtils::isConfigurator))
                    .orElse(configurators);
            // 根据Override协议修改
            doOverrideIfNecessary();
        }

        public synchronized void doOverrideIfNecessary() {
            final Invoker<?> invoker;
            if (originInvoker instanceof InvokerDelegate) {
                invoker = ((InvokerDelegate<?>) originInvoker).getInvoker();
            } else {
                invoker = originInvoker;
            }
            //The origin invoker 当前服务的原始服务提供者url
            URL originUrl = RegistryProtocol.this.getProviderUrl(invoker);
            String key = getCacheKey(originInvoker);

            ExporterChangeableWrapper<?> exporter = bounds.get(key);
            if (exporter == null) {
                logger.warn(new IllegalStateException("error state, exporter should not be null"));
                return;
            }

            //The current, may have been merged many times，当前服务被导出的url
            URL currentUrl = exporter.getInvoker().getUrl();

            //根据configurators修改url，configurators是全量的，并不是某个新增的或删除的，所以是基于原始的url进行修改，并不是基于currentUrl
            //Merged with this configuration
            URL newUrl = getConfigedInvokerUrl(configurators, originUrl);

            newUrl = getConfigedInvokerUrl(providerConfigurationListener.getConfigurators(), newUrl);
            newUrl = getConfigedInvokerUrl(serviceConfigurationListeners.get(originUrl.getServiceKey())
                    .getConfigurators(), newUrl);

            // 修改过的url如果和目前的url不相同，则重新按newUrl导出
            if (!currentUrl.equals(newUrl)) {
                RegistryProtocol.this.reExport(originInvoker, newUrl);
                logger.info("exported provider url changed, origin url: " + originUrl +
                        ", old export url: " + currentUrl + ", new export url: " + newUrl);
            }
        }

        private List<URL> getMatchedUrls(List<URL> configuratorUrls, URL currentSubscribe) {
            List<URL> result = new ArrayList<URL>();
            for (URL url : configuratorUrls) {
                URL overrideUrl = url;
                // Compatible with the old version
                if (url.getParameter(CATEGORY_KEY) == null && OVERRIDE_PROTOCOL.equals(url.getProtocol())) {
                    overrideUrl = url.addParameter(CATEGORY_KEY, CONFIGURATORS_CATEGORY);
                }

                // Check whether url is to be applied to the current service
                if (UrlUtils.isMatch(currentSubscribe, overrideUrl)) {
                    result.add(url);
                }
            }
            return result;
        }
    }

    private class ServiceConfigurationListener extends AbstractConfiguratorListener {
        private URL providerUrl;
        private OverrideListener notifyListener;

        public ServiceConfigurationListener(URL providerUrl, OverrideListener notifyListener) {
            this.providerUrl = providerUrl;
            this.notifyListener = notifyListener;
            // 订阅 服务接口名+group+version+".configurators"
            this.initWith(DynamicConfiguration.getRuleKey(providerUrl) + CONFIGURATORS_SUFFIX);
        }

        private <T> URL overrideUrl(URL providerUrl) {
            return RegistryProtocol.getConfigedInvokerUrl(configurators, providerUrl);
        }

        @Override
        protected void notifyOverrides() {
            notifyListener.doOverrideIfNecessary();
        }
    }

    //
    private class ProviderConfigurationListener extends AbstractConfiguratorListener {

        public ProviderConfigurationListener() {
            // // 订阅 应用名+".configurators"
            this.initWith(ApplicationModel.getApplication() + CONFIGURATORS_SUFFIX);
        }

        /**
         * Get existing configuration rule and override provider url before exporting.
         *
         * @param providerUrl
         * @param <T>
         * @return
         */
        private <T> URL overrideUrl(URL providerUrl) {
            // 通过configurators去修改/装配providerUrl
            return RegistryProtocol.getConfigedInvokerUrl(configurators, providerUrl);
        }

        @Override
        protected void notifyOverrides() {
            overrideListeners.values().forEach(listener -> ((OverrideListener) listener).doOverrideIfNecessary());
        }
    }

    /**
     * exporter proxy, establish the corresponding relationship between the returned exporter and the exporter
     * exported by the protocol, and can modify the relationship at the time of override.
     *
     * @param <T>
     */
    private class ExporterChangeableWrapper<T> implements Exporter<T> {

        private final ExecutorService executor = newSingleThreadExecutor(new NamedThreadFactory("Exporter-Unexport", true));

        private final Invoker<T> originInvoker;
        private Exporter<T> exporter;
        private URL subscribeUrl;
        private URL registerUrl;

        public ExporterChangeableWrapper(Exporter<T> exporter, Invoker<T> originInvoker) {
            this.exporter = exporter;
            this.originInvoker = originInvoker;
        }

        public Invoker<T> getOriginInvoker() {
            return originInvoker;
        }

        @Override
        public Invoker<T> getInvoker() {
            return exporter.getInvoker();
        }

        public void setExporter(Exporter<T> exporter) {
            this.exporter = exporter;
        }

        @Override
        public void unexport() {
            String key = getCacheKey(this.originInvoker);
            bounds.remove(key);

            // 从注册中心删除服务URL
            Registry registry = RegistryProtocol.INSTANCE.getRegistry(originInvoker);
            try {
                registry.unregister(registerUrl);
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }


            try {
                // 解绑当前服务的Listener
                NotifyListener listener = RegistryProtocol.INSTANCE.overrideListeners.remove(subscribeUrl);
                registry.unsubscribe(subscribeUrl, listener);
                DynamicConfiguration.getDynamicConfiguration()
                        .removeListener(subscribeUrl.getServiceKey() + CONFIGURATORS_SUFFIX,
                                serviceConfigurationListeners.get(subscribeUrl.getServiceKey()));
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }

            executor.submit(() -> {
                try {
                    int timeout = ConfigurationUtils.getServerShutdownTimeout();
                    if (timeout > 0) {
                        logger.info("Waiting " + timeout + "ms for registry to notify all consumers before unexport. " +
                                "Usually, this is called when you use dubbo API");
                        Thread.sleep(timeout);
                    }
                    exporter.unexport();
                } catch (Throwable t) {
                    logger.warn(t.getMessage(), t);
                }
            });
        }

        public void setSubscribeUrl(URL subscribeUrl) {
            this.subscribeUrl = subscribeUrl;
        }

        public void setRegisterUrl(URL registerUrl) {
            this.registerUrl = registerUrl;
        }
    }
}
