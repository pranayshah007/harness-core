/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.AddStepDetailsInstanceRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.response.publishers.SdkResponseEventPublisher;
import io.harness.pms.sdk.core.steps.executables.StepDetailsInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SdkGraphVisualizationDataServiceImpl implements SdkGraphVisualizationDataService {
  @Inject private SdkResponseEventPublisher sdkResponseEventPublisher;

  @Override
  public void publishStepDetailInformation(Ambiance ambiance, StepDetailsInfo stepDetailsInfo, String name) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    try {
      log.info("Publishing stepDetail update event for given nodeExecutionId: {} ", nodeExecutionId);
      AddStepDetailsInstanceRequest addStepDetailsInstanceRequest = AddStepDetailsInstanceRequest.newBuilder()
                                                                        .setStepDetails(stepDetailsInfo.toViewJson())
                                                                        .setName(name)
                                                                        .setNodeExecutionId(nodeExecutionId)
                                                                        .build();
      SdkResponseEventProto sdkResponseEvent =
          SdkResponseEventProto.newBuilder()
              .setSdkResponseEventType(SdkResponseEventType.ADD_STEP_DETAILS_INSTANCE_REQUEST)
              .setAmbiance(ambiance)
              .setStepDetailsInstanceRequest(addStepDetailsInstanceRequest)
              .build();
      sdkResponseEventPublisher.publishEvent(sdkResponseEvent);
      log.info("Published stepDetail update event for given nodeExecutionId: {} ", nodeExecutionId);
    } catch (Exception e) {
      log.error("Failed to published stepDetail update event for given nodeExecutionId: {} ", nodeExecutionId, e);
      throw e;
    }
  }

  @Override
  public void publishStepDetailInformation(
      Ambiance ambiance, StepDetailsInfo stepDetailsInfo, String name, StepCategory category) {
    String nodeExecutionId = AmbianceUtils.getRuntimeIdForGivenCategory(ambiance, category);
    try {
      log.info("Publishing stepDetail update event for given nodeExecutionId: {} ", nodeExecutionId);
      AddStepDetailsInstanceRequest addStepDetailsInstanceRequest = AddStepDetailsInstanceRequest.newBuilder()
                                                                        .setStepDetails(stepDetailsInfo.toViewJson())
                                                                        .setName(name)
                                                                        .setNodeExecutionId(nodeExecutionId)
                                                                        .build();
      SdkResponseEventProto sdkResponseEvent =
          SdkResponseEventProto.newBuilder()
              .setSdkResponseEventType(SdkResponseEventType.ADD_STEP_DETAILS_INSTANCE_REQUEST)
              .setAmbiance(ambiance)
              .setStepDetailsInstanceRequest(addStepDetailsInstanceRequest)
              .build();
      sdkResponseEventPublisher.publishEvent(sdkResponseEvent);
      log.info("Published stepDetail update event for given nodeExecutionId: {} ", nodeExecutionId);
    } catch (Exception e) {
      log.error("Failed to published stepDetail update event for given nodeExecutionId: {} ", nodeExecutionId, e);
      throw e;
    }
  }
}
