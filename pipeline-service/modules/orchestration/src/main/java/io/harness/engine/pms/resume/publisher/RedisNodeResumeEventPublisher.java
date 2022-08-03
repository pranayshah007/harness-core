/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume.publisher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.pms.execution.strategy.identity.IdentityStep;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.TaskChainExecutableResponse;
import io.harness.pms.contracts.resume.ChainDetails;
import io.harness.pms.contracts.resume.NodeResumeEvent;
import io.harness.pms.events.base.PmsEventCategory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class RedisNodeResumeEventPublisher implements NodeResumeEventPublisher {
  @Inject private PmsEventSender eventSender;

  @Override
  public void publishEvent(ResumeMetadata resumeMetadata, Map<String, ByteString> responseMap, boolean isError) {
    NodeResumeEvent.Builder resumeEventBuilder = NodeResumeEvent.newBuilder()
                                                     .setAmbiance(resumeMetadata.getAmbiance())
                                                     .setExecutionMode(resumeMetadata.getMode())
                                                     .setStepParameters(resumeMetadata.getResolvedStepParameters())
                                                     .setAsyncError(isError)
                                                     .putAllResponse(responseMap);

    ChainDetails chainDetails = buildChainDetails(resumeMetadata);
    if (chainDetails != null) {
      resumeEventBuilder.setChainDetails(chainDetails);
    }

    eventSender.sendEvent(resumeEventBuilder.getAmbiance(), resumeEventBuilder.build().toByteString(),
        PmsEventCategory.NODE_RESUME, resumeMetadata.getModule(), true);
  }

  @Override
  public void publishEventForIdentityNode(
      ResumeMetadata resumeMetadata, Map<String, ByteString> responseMap, boolean isError, String serviceName) {
    NodeResumeEvent.Builder resumeEventBuilder =
        NodeResumeEvent.newBuilder()
            .setAmbiance(IdentityStep.modifyAmbiance(resumeMetadata.getAmbiance()))
            .setExecutionMode(resumeMetadata.getMode())
            .setStepParameters(resumeMetadata.getResolvedStepParameters())
            .setAsyncError(isError)
            .putAllResponse(responseMap);

    ChainDetails chainDetails = buildChainDetails(resumeMetadata);
    if (chainDetails != null) {
      resumeEventBuilder.setChainDetails(chainDetails);
    }

    eventSender.sendEvent(resumeEventBuilder.getAmbiance(), resumeEventBuilder.build().toByteString(),
        PmsEventCategory.NODE_RESUME, serviceName, true);
  }

  public ChainDetails buildChainDetails(ResumeMetadata resumeMetadata) {
    ExecutionMode mode = resumeMetadata.getMode();

    if (mode == ExecutionMode.TASK_CHAIN || mode == ExecutionMode.CHILD_CHAIN) {
      switch (mode) {
        case TASK_CHAIN:
          TaskChainExecutableResponse lastLinkResponse =
              Objects.requireNonNull(resumeMetadata.getLatestExecutableResponse()).getTaskChain();
          return ChainDetails.newBuilder()
              .setIsEnd(lastLinkResponse.getChainEnd())
              .setPassThroughData(lastLinkResponse.getPassThroughData())
              .build();
        case CHILD_CHAIN:
          ChildChainExecutableResponse lastChildChainExecutableResponse = Preconditions.checkNotNull(
              Objects.requireNonNull(resumeMetadata.getLatestExecutableResponse()).getChildChain());
          boolean chainEnd =
              lastChildChainExecutableResponse.getLastLink() || lastChildChainExecutableResponse.getSuspend();
          return ChainDetails.newBuilder()
              .setIsEnd(chainEnd)
              .setPassThroughData(lastChildChainExecutableResponse.getPassThroughData())
              .build();
        default:
          log.error("This Should Not Happen not a chain mode");
      }
    }
    return null;
  }
}
