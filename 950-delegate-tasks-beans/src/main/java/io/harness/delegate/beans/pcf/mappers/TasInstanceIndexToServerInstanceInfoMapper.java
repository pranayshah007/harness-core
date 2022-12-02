/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.beans.pcf.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.task.pcf.response.TasInfraConfig;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.cloudfoundry.operations.applications.ApplicationDetail;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class TasInstanceIndexToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(
      List<String> instanceIndices, TasInfraConfig tasInfraConfig, ApplicationDetail applicationDetail) {
    return instanceIndices.stream()
        .map(index
            -> TasServerInstanceInfo.builder()
                   .instanceIndex(index)
                   .tasApplicationGuid(applicationDetail.getId())
                   .tasApplicationName(applicationDetail.getName())
                   .organization(tasInfraConfig.getOrganization())
                   .space(tasInfraConfig.getSpace())
                   .id(applicationDetail.getId() + ":" + index)
                   .build())
        .collect(Collectors.toList());
  }
}
