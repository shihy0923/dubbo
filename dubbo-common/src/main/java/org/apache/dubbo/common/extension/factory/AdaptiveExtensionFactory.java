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
package org.apache.dubbo.common.extension.factory;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionFactory;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AdaptiveExtensionFactory
 */
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {

    private final List<ExtensionFactory> factories;

    public AdaptiveExtensionFactory() {
        // 支持哪些ExtensionFactory (Spi, SPring)
        ExtensionLoader<ExtensionFactory> loader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);//这个loader的objectFactory属性为null

        List<ExtensionFactory> list = new ArrayList<ExtensionFactory>();

        for (String name : loader.getSupportedExtensions()) { // spi, spring，解析扩展点的类，其实就是从接口的SPI和配置文件中获取我们自己的扩展类，放入map中，其实就是cachedClasses中的值
            list.add(loader.getExtension(name));//获取扩展了ExtensionFactory接口的扩展类，一般是SpringExtensionFactory,, SpiExtensionFactory
        }
        factories = Collections.unmodifiableList(list);//将list的值赋值给factories这个成员变量
    }

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // 遍历两个ExtensionFactory，从ExtensionFactory中得到实例，只要从某个ExtensionFactory中获取到对象实例就可以了
        for (ExtensionFactory factory : factories) {
            T extension = factory.getExtension(type, name);  // SpringExtensionFactory, SpiExtensionFactory
            if (extension != null) {//从SpringExtensionFactory,, SpiExtensionFactory中获取任何一个要被ioc注入的对象就返回。如果是SpringExtensionFactory，则从spring容器中获取，如果是SpiExtensionFactory,则获取标注了@Adaptive的类的对象，或者dubbo自动帮我们生成成那个代理类对象
                return extension;
            }
        }
        return null;
    }

}
