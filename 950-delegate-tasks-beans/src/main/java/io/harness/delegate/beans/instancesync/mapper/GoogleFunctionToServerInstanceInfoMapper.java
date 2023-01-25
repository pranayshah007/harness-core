/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.googlefunction.GoogleCloudFunction;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionToServerInstanceInfoMapper {
  public ServerInstanceInfo toServerInstanceInfo(
      GoogleFunction googleFunction, String project, String region, String infraStructureKey) {
    return GoogleFunctionServerInstanceInfo.builder()
        .functionName(googleFunction.getFunctionName())
        .project(project)
        .region(region)
        .revision(googleFunction.getCloudRunService().getRevision())
        .source(googleFunction.getSource())
        .updatedTime(googleFunction.getUpdatedTime())
        .memorySize(googleFunction.getCloudRunService().getMemory())
        .runTime(googleFunction.getRuntime())
        .infraStructureKey(infraStructureKey)
        .build();
  }
}
