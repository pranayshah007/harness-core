/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.demo.changesource;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.HarnessCDEventMetadata;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource;
import io.harness.cvng.core.services.api.demo.ChangeSourceDemoDataGenerator;
import io.harness.cvng.core.utils.DateTimeUtils;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class CDNGChangeSourceDemoDataGenerator implements ChangeSourceDemoDataGenerator<HarnessCDChangeSource> {
  @Inject private Clock clock;
  @Override
  public List<ChangeEventDTO> generate(HarnessCDChangeSource changeSource) {
    Instant time = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    return Arrays.asList(ChangeEventDTO.builder()
                             .accountId(changeSource.getAccountId())
                             .changeSourceIdentifier(changeSource.getIdentifier())
                             .projectIdentifier(changeSource.getProjectIdentifier())
                             .orgIdentifier(changeSource.getOrgIdentifier())
                             .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
                             .eventTime(time.toEpochMilli())
                             .type(ChangeSourceType.HARNESS_CD)
                             .metadata(HarnessCDEventMetadata.builder()
                                           .artifactTag("build#1")
                                           .deploymentStartTime(time.minus(Duration.ofMinutes(5)).toEpochMilli())
                                           .deploymentEndTime(time.toEpochMilli())
                                           .stageStepId("stageStepId")
                                           .stageId("stageId")
                                           .pipelineId("demoPipelineId")
                                           .runSequence("1")
                                           .planExecutionId("executionId")
                                           .artifactType("artifactType")
                                           .status("SUCCESS")
                                           .build())
                             .build());
  }
}
