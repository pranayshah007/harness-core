/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import static java.util.Objects.isNull;

import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.NotSupportedException;

@Singleton
public class CgInstanceSyncV2DeploymentHelperFactory {
  private final K8sInstanceSyncV2DeploymentHelperCg k8sHelper;

  private final ConcurrentHashMap<SettingVariableTypes, CgInstanceSyncV2DeploymentHelper> holder;

  @Inject
  public CgInstanceSyncV2DeploymentHelperFactory(K8sInstanceSyncV2DeploymentHelperCg k8sHelper) {
    this.holder = new ConcurrentHashMap<>();
    this.k8sHelper = k8sHelper;

    initHelpers();
  }

  private void initHelpers() {
    this.holder.put(SettingVariableTypes.KUBERNETES_CLUSTER, k8sHelper);
  }

  public CgInstanceSyncV2DeploymentHelper getHelper(SettingVariableTypes cloudProviderType) {
    if (isNull(this.holder.getOrDefault(cloudProviderType, null))) {
      throw new NotSupportedException(
          String.format("Cloud Provider Type [%s] is not supported for Instance sync V2", cloudProviderType));
    } else {
      return this.holder.getOrDefault(cloudProviderType, null);
    }
  }
}
