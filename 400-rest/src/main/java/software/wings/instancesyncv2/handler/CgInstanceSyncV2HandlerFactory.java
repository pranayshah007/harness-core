/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CgInstanceSyncV2HandlerFactory {
  private final K8sInstanceSyncV2HandlerCg k8sHandler;

  private final ConcurrentHashMap<SettingVariableTypes, CgInstanceSyncV2Handler> holder;

  @Inject
  public CgInstanceSyncV2HandlerFactory(K8sInstanceSyncV2HandlerCg k8sHandler) {
    this.holder = new ConcurrentHashMap<>();
    this.k8sHandler = k8sHandler;

    initHandlers();
  }

  private void initHandlers() {
    this.holder.put(SettingVariableTypes.KUBERNETES_CLUSTER, k8sHandler);
  }

  public CgInstanceSyncV2Handler getHandler(SettingVariableTypes cloudProviderType) {
    return this.holder.getOrDefault(cloudProviderType, null);
  }
}
