/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class EcsTaskToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(

      List<EcsTask> ecsTasks, String infraStructureKey, String region) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(ecsTasks)) {
      serverInstanceInfoList = ecsTasks.stream()
                                   .map(task -> toServerInstanceInfo(task, infraStructureKey, region))
                                   .collect(Collectors.toList());
    }
    return serverInstanceInfoList;
  }

  public ServerInstanceInfo toServerInstanceInfo(EcsTask ecsTask, String infraStructureKey, String region) {
    return EcsServerInstanceInfo.builder()
        .region(region)
        .clusterArn(ecsTask.getClusterArn())
        .serviceName(ecsTask.getServiceName())
        .launchType(ecsTask.getLaunchType())
        .taskArn(ecsTask.getTaskArn())
        .taskDefinitionArn(ecsTask.getTaskDefinitionArn())
        .containers(ecsTask.getContainers())
        .startedAt(ecsTask.getStartedAt())
        .startedBy(ecsTask.getStartedBy())
        .version(ecsTask.getVersion())
        .infraStructureKey(infraStructureKey)
        .build();
  }
}
