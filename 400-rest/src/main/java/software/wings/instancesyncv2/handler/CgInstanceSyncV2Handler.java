/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;

import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;

import java.util.List;
import java.util.Set;

public interface CgInstanceSyncV2Handler {
  PerpetualTaskExecutionBundle fetchInfraConnectorDetails(SettingAttribute cloudProvider);

  InstanceSyncTaskDetails prepareTaskDetails(
      DeploymentSummary deploymentSummary, String cloudProviderId, String perpetualTaskId);

  Set<CgReleaseIdentifiers> buildReleaseIdentifiers(DeploymentInfo deploymentInfo);

  Set<CgReleaseIdentifiers> mergeReleaseIdentifiers(
      Set<CgReleaseIdentifiers> releaseIdentifiers, Set<CgReleaseIdentifiers> buildReleaseIdentifiers);

  List<CgDeploymentReleaseDetails> getDeploymentReleaseDetails(InstanceSyncTaskDetails taskDetails);

  boolean isDeploymentInfoTypeSupported(Class<? extends DeploymentInfo> deploymentInfoClazz);

  List<Instance> getDeployedInstances(DeploymentSummary deploymentSummary);

  List<Instance> difference(List<Instance> list1, List<Instance> list2);

  List<Instance> getDeployedInstances(
      List<InstanceInfo> instanceInfos, List<Instance> instancesInDb, Instance lastDiscoveredInstance);

  List<Instance> instancesToUpdate(List<Instance> instances, List<Instance> instancesInDb);
}
