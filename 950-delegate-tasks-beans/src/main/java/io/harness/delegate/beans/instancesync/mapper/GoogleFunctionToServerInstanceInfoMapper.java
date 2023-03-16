/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(
      GoogleFunction googleFunction, String project, String region, String infraStructureKey) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(googleFunction.getActiveCloudRunRevisions())) {
      serverInstanceInfoList = googleFunction.getActiveCloudRunRevisions()
                                   .stream()
                                   .map(googleCloudRunRevision
                                       -> toServerInstanceInfo(googleFunction, googleCloudRunRevision.getRevision(),
                                           project, region, infraStructureKey))
                                   .collect(Collectors.toList());
    }

    return serverInstanceInfoList;
  }

  public ServerInstanceInfo toServerInstanceInfo(
      GoogleFunction googleFunction, String revision, String project, String region, String infraStructureKey) {
    return GoogleFunctionServerInstanceInfo.builder()
        .functionName(googleFunction.getFunctionName())
        .project(project)
        .region(region)
        .revision(revision)
        .source(googleFunction.getSource())
        .updatedTime(googleFunction.getUpdatedTime())
        .memorySize(googleFunction.getCloudRunService().getMemory())
        .runTime(googleFunction.getRuntime())
        .infraStructureKey(infraStructureKey)
        .build();
  }
}
