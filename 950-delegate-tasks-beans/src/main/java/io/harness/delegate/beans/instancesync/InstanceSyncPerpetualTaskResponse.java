/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import java.util.List;

@OwnedBy(HarnessTeam.DX)
public interface InstanceSyncPerpetualTaskResponse extends DelegateTaskNotifyResponseData {
  List<ServerInstanceInfo> getServerInstanceDetails();
  String getDeploymentType();
}
