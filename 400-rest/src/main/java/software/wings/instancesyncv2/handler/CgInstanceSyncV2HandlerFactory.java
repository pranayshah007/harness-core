/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import software.wings.api.DeploymentInfo;
import software.wings.api.K8sDeploymentInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CgInstanceSyncV2HandlerFactory {
  @Inject private K8sInstanceSyncV2HandlerCg k8sHandler;

  private ConcurrentHashMap<Class<? extends DeploymentInfo>, CgInstanceSyncV2Handler> holder;

  public CgInstanceSyncV2HandlerFactory() {
    this.holder = new ConcurrentHashMap<>();

    initHandlers();
  }

  private void initHandlers() {
    this.holder.put(K8sDeploymentInfo.class, k8sHandler);
  }

  public CgInstanceSyncV2Handler getHandler(DeploymentInfo deploymentInfo) {
    return this.holder.getOrDefault(deploymentInfo.getClass(), null);
  }
}
