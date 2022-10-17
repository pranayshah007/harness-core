/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import software.wings.api.DeploymentSummary;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Named("k8sInstanceSyncV2HandlerCg")
public class K8sInstanceSyncV2HandlerCg implements CgInstanceSyncV2Handler {
  @Override
  public SettingValue fetchInfraConnectorDetails(DeploymentSummary deploymentSummary) {
    return null;
  }
}
