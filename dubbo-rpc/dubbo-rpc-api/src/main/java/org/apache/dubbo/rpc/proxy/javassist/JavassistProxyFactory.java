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
package org.apache.dubbo.rpc.proxy.javassist;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.bytecode.Proxy;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.proxy.AbstractProxyFactory;
import org.apache.dubbo.rpc.proxy.AbstractProxyInvoker;
import org.apache.dubbo.rpc.proxy.InvokerInvocationHandler;

/**
 * JavaassistRpcProxyFactory
 */
public class JavassistProxyFactory extends AbstractProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {

        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        // 如果现在被代理的对象proxy本身就是一个已经被代理过的对象，那么则取代理类的Wrapper，否则取type（接口）的Wrapper
        // Wrapper是针对某个类或某个接口的包装类，通过wrapper对象可以更方便的去执行某个类或某个接口的方法

        //由于是在内存中直接生成的字节码，其源码是下面的。里面会有“$”符号，看官网解释http://www.javassist.org/tutorial/tutorial2.html
        //$0标识this，$1、$2这些表示按照顺序表示方法形参
        /*
        *
package org.apache.dubbo.common.bytecode;
class Wrapper0 {
        public static String[] pns;

        public static java.util.Map pts;

        public static String[] mns;

        public static String[] dmns;

        public static Class[] mts0;

        public static Class[] mts1;

        public static Class[] mts2;


        public String[] getPropertyNames() {
            return pns;
        }

        public boolean hasProperty(String n) {
            return pts.containsKey($1);
        }

        public Class getPropertyType(String n) {
            return (Class) pts.get($1);
        }

        public String[] getMethodNames() {
            return mns;
        }

        public String[] getDeclaredMethodNames() {
            return dmns;
        }

        public void setPropertyValue(Object o, String n, Object v) {
            com.tuling.DemoService w;
            try {
                w = ((com.tuling.DemoService) $1);
            } catch (Throwable e) {
                throw new IllegalArgumentException(e);
            }
            throw new org.apache.dubbo.common.bytecode.NoSuchPropertyException(
                    "Not found property \"" + $2 + "\" field or setter method in class com.tuling.DemoService.");
        }

        public Object getPropertyValue(Object o, String n) {
            com.tuling.DemoService w;
            try {
                w = ((com.tuling.DemoService) $1);
            } catch (Throwable e) {
                throw new IllegalArgumentException(e);
            }
            throw new org.apache.dubbo.common.bytecode.NoSuchPropertyException(
                    "Not found property \"" + $2 + "\" field or setter method in class com.tuling.DemoService.");
        }

        public Object invokeMethod(Object o, String n, Class[] p, Object[] v)
                throws java.lang.reflect.InvocationTargetException {
            com.tuling.DemoService w;
            try {
                w = ((com.tuling.DemoService) $1);
            } catch (Throwable e) {
                throw new IllegalArgumentException(e);
            }
            try {
                if ("sayHello".equals($2) && $3.length == 1 && $3[0].getName().equals("java.lang.String")) {
                    return ($w) w.sayHello((java.lang.String) $4[0]);
                }
                if ("sayHello".equals($2) && $3.length == 3 && $3[0].getName().equals("java.lang.String") &&
                    $3[1].getName().equals("java.lang.String") &&
                    $3[2].getName().equals("com.tuling.DemoServiceListener")) {
                    return ($w) w.sayHello((java.lang.String) $4[0], (java.lang.String) $4[1],
                                           (com.tuling.DemoServiceListener) $4[2]);
                }
                if ("sayHelloAsync".equals($2) && $3.length == 1) {
                    return ($w) w.sayHelloAsync((java.lang.String) $4[0]);
                }
            } catch (Throwable e) {
                throw new java.lang.reflect.InvocationTargetException(e);
            }
            throw new org.apache.dubbo.common.bytecode.NoSuchMethodException(
                    "Not found method \"" + $2 + "\" in class com.tuling.DemoService.");
        }

    }
        * */
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);

        // proxy是服务实现类
        // type是服务接口
        // url是一个注册中心url，但同时也记录了
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {

                // 执行proxy的method方法
                // 执行的proxy实例的方法
                // 如果没有wrapper，则要通过原生的反射技术去获取Method对象，然后执行
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

}
