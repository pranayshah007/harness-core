/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancedashboardservice;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.models.ActiveServiceInstanceInfo;
import io.harness.models.BuildsByEnvironment;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.dashboard.InstanceCountDetails;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;

import java.util.List;

@OwnedBy(HarnessTeam.DX)
public interface InstanceDashboardService {
  InstanceCountDetails getActiveInstanceCountDetailsByEnvType(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);
  List<BuildsByEnvironment> getActiveInstancesByServiceIdGroupedByEnvironmentAndBuild(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs);
  List<EnvBuildInstanceCount> getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs);
  List<ActiveServiceInstanceInfo> getActiveServiceInstanceInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId);
  List<InstanceDetailsByBuildId> getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String envId, List<String> buildIds,
      long timestampInMs);
  InstanceCountDetailsByEnvTypeAndServiceId getActiveServiceInstanceCountBreakdown(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<String> serviceId, long timestampInMs);
}
