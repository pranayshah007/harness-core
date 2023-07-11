/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateResponseData;

import software.wings.beans.InfrastructureMapping;
import software.wings.service.InstanceSyncPerpetualTaskCreator;

import java.util.Optional;

@OwnedBy(PL)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
public interface InstanceSyncByPerpetualTaskHandler {
  Optional<FeatureName> getFeatureFlagToEnablePerpetualTaskForInstanceSync();

  InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator();

  void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response);

  default void cleanupInvalidV1PerpetualTask(String accountId) {}

  Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response);
}
