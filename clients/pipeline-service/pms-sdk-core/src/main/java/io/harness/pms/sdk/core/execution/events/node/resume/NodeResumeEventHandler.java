/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.resume;

import static io.harness.pms.contracts.execution.Status.ABORTED;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.contracts.resume.NodeResumeEvent;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.execution.ChainDetails;
import io.harness.pms.sdk.core.execution.ChainDetails.ChainDetailsBuilder;
import io.harness.pms.sdk.core.execution.EngineObtainmentHelper;
import io.harness.pms.sdk.core.execution.ExecutableProcessor;
import io.harness.pms.sdk.core.execution.ExecutableProcessorFactory;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.ResumePackage.ResumePackageBuilder;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class NodeResumeEventHandler extends PmsBaseEventHandler<NodeResumeEvent> {
  @Inject private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject private EngineObtainmentHelper engineObtainmentHelper;
  @Inject private ExecutableProcessorFactory executableProcessorFactory;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  protected String getMetricPrefix(NodeResumeEvent message) {
    return "resume_event";
  }

  @Override
  @NonNull
  protected Map<String, String> extraLogProperties(NodeResumeEvent event) {
    return ImmutableMap.<String, String>builder().put("eventType", NodeExecutionEventType.RESUME.name()).build();
  }

  @Override
  protected Ambiance extractAmbiance(NodeResumeEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected void handleEventWithContext(NodeResumeEvent event) {
    ExecutableProcessor processor = executableProcessorFactory.obtainProcessor(event.getExecutionMode());
    Map<String, ResponseData> response = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(event.getResponseMap())) {
      event.getResponseMap().forEach(
          (k, v) -> response.put(k, (ResponseData) kryoSerializer.asInflatedObject(v.toByteArray())));
    }

    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance());
    Preconditions.checkArgument(isNotBlank(nodeExecutionId), "nodeExecutionId is null or empty");
    try {
      if (event.getAsyncError()) {
        log.info("Async Error for the Event Sending Error Response");
        ErrorResponseData errorResponseData = (ErrorResponseData) response.values().iterator().next();
        StepResponseProto stepResponse =
            StepResponseProto.newBuilder()
                .setStatus(Status.ERRORED)
                .setFailureInfo(FailureInfo.newBuilder()
                                    .addAllFailureTypes(EngineExceptionUtils.transformToOrchestrationFailureTypes(
                                        errorResponseData.getFailureTypes()))
                                    .setErrorMessage(errorResponseData.getErrorMessage())
                                    .build())
                .build();
        sdkNodeExecutionService.handleStepResponse(event.getAmbiance(), stepResponse);
        return;
      }

      processor.handleResume(buildResumePackage(event, response));
    } catch (Exception ex) {
      log.error("Error while resuming execution", ex);
      sdkNodeExecutionService.handleStepResponse(event.getAmbiance(), NodeExecutionUtils.constructStepResponse(ex));
    }
  }

  private ResumePackage buildResumePackage(NodeResumeEvent event, Map<String, ResponseData> response) {
    StepParameters stepParameters =
        RecastOrchestrationUtils.fromJson(event.getStepParameters().toStringUtf8(), StepParameters.class);

    ResumePackageBuilder builder =
        ResumePackage.builder()
            .ambiance(event.getAmbiance())
            .stepParameters(stepParameters)
            .stepInputPackage(engineObtainmentHelper.obtainInputPackage(event.getAmbiance(), event.getRefObjectsList()))
            .responseDataMap(response);

    if (event.hasChainDetails()) {
      io.harness.pms.contracts.resume.ChainDetails chainDetailsProto = event.getChainDetails();
      ChainDetailsBuilder chainDetailsBuilder = ChainDetails.builder().shouldEnd(calculateIsEnd(event, response));
      if (EmptyPredicate.isNotEmpty(chainDetailsProto.getPassThroughData())) {
        ByteString passThroughBytes = chainDetailsProto.getPassThroughData();
        if (event.getExecutionMode() == ExecutionMode.CHILD_CHAIN) {
          chainDetailsBuilder.passThroughBytes(passThroughBytes);
        } else if (event.getExecutionMode() == ExecutionMode.TASK_CHAIN) {
          chainDetailsBuilder.passThroughData(
              RecastOrchestrationUtils.fromBytes(passThroughBytes.toByteArray(), PassThroughData.class));
        }
      }
      builder.chainDetails(chainDetailsBuilder.build());
    }
    return builder.build();
  }

  public boolean calculateIsEnd(NodeResumeEvent event, Map<String, ResponseData> response) {
    if (event.getExecutionMode() != ExecutionMode.CHILD_CHAIN) {
      return event.getChainDetails().getIsEnd();
    }
    return event.getChainDetails().getIsEnd() || isBroken(response) || isAborted(response);
  }

  private boolean isBroken(Map<String, ResponseData> accumulatedResponse) {
    return accumulatedResponse.values().stream().anyMatch(stepNotifyResponse
        -> StatusUtils.brokeStatuses().contains(((StepResponseNotifyData) stepNotifyResponse).getStatus()));
  }

  private boolean isAborted(Map<String, ResponseData> accumulatedResponse) {
    return accumulatedResponse.values().stream().anyMatch(
        stepNotifyResponse -> ABORTED == (((StepResponseNotifyData) stepNotifyResponse).getStatus()));
  }
}
