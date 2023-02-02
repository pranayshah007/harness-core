/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AsgServerInstanceInfo;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.delegate.task.aws.asg.AutoScalingGroupInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class AutoScalingGroupContainerToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(

      AutoScalingGroupContainer autoScalingGroupContainer, String infraStructureKey, String region,
      String executionStrategy, String asgNameWithoutSuffix, Boolean production) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    List<AutoScalingGroupInstance> autoScalingGroupInstanceList =
        autoScalingGroupContainer.getAutoScalingGroupInstanceList();

    if (CollectionUtils.isNotEmpty(autoScalingGroupInstanceList)) {
      serverInstanceInfoList = autoScalingGroupInstanceList.stream()
                                   .map(autoScalingGroupInstance
                                       -> toServerInstanceInfo(autoScalingGroupInstance, infraStructureKey, region,
                                           executionStrategy, asgNameWithoutSuffix, production))
                                   .collect(Collectors.toList());
    }
    return serverInstanceInfoList;
  }

  public ServerInstanceInfo toServerInstanceInfo(AutoScalingGroupInstance autoScalingGroupInstance,
      String infrastructureKey, String region, String executionStrategy, String asgNameWithoutSuffix,
      Boolean production) {
    return AsgServerInstanceInfo.builder()
        .region(region)
        .infrastructureKey(infrastructureKey)
        .asgName(autoScalingGroupInstance.getAutoScalingGroupName())
        .asgNameWithoutSuffix(asgNameWithoutSuffix)
        .instanceId(autoScalingGroupInstance.getInstanceId())
        .executionStrategy(executionStrategy)
        .production(production)
        .build();
  }
}
