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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.support.FailoverCluster;

/**
 * Cluster. (SPI, Singleton, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Computer_cluster">Cluster</a>
 * <a href="http://en.wikipedia.org/wiki/Fault-tolerant_system">Fault-Tolerant</a>
 */

/*
    *  package org.apache.dubbo.rpc.cluster;
import org.apache.dubbo.common.extension.ExtensionLoader;

    public class Cluster$Adaptive implements org.apache.dubbo.rpc.cluster.Cluster {
        public org.apache.dubbo.rpc.Invoker join(org.apache.dubbo.rpc.cluster.Directory arg0)
                throws org.apache.dubbo.rpc.RpcException {
            if (arg0 == null) {
                throw new IllegalArgumentException("org.apache.dubbo.rpc.cluster.Directory argument == null");
            }
            if (arg0.getUrl() == null) {
                throw new IllegalArgumentException("org.apache.dubbo.rpc.cluster.Directory argument getUrl() == null");
            }
            org.apache.dubbo.common.URL url = arg0.getUrl();
            String extName = url.getParameter("cluster", "failover");
            if (extName == null) {
                throw new IllegalStateException(
                        "Failed to get extension (org.apache.dubbo.rpc.cluster.Cluster) name from url (" +
                        url.toString() +
                        ") use keys([cluster])");
            }
            org.apache.dubbo.rpc.cluster.Cluster extension =
                    (org.apache.dubbo.rpc.cluster.Cluster) ExtensionLoader.getExtensionLoader(
                            org.apache.dubbo.rpc.cluster.Cluster.class).getExtension(extName);
            return extension.join(arg0);
        }
    }
    * */

@SPI(FailoverCluster.NAME)
public interface Cluster {

    /**
     * Merge the directory invokers to a virtual invoker.
     *
     * @param <T>
     * @param directory
     * @return cluster invoker
     * @throws RpcException
     */
    @Adaptive
    <T> Invoker<T> join(Directory<T> directory) throws RpcException;

}