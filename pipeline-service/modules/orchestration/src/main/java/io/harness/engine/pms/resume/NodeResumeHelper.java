/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.resume.publisher.NodeResumeEventPublisher;
import io.harness.engine.pms.resume.publisher.ResumeMetadata;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.serializer.KryoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeResumeHelper {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private NodeResumeEventPublisher nodeResumeEventPublisher;

  public void resume(NodeExecution nodeExecution, Map<String, ResponseDataProto> responseMap, boolean isError) {
    ResumeMetadata resumeMetadata = ResumeMetadata.fromNodeExecution(nodeExecution);
    nodeResumeEventPublisher.publishEvent(resumeMetadata, buildResponseMap(resumeMetadata, responseMap), isError);
  }

  @VisibleForTesting
  Map<String, ResponseDataProto> buildResponseMap(
      ResumeMetadata resumeMetadata, Map<String, ResponseDataProto> response) {
    Map<String, ResponseDataProto> byteResponseMap = new HashMap<>();
    if (accumulationRequired(resumeMetadata)) {
      List<NodeExecution> childExecutions = new LinkedList<>();
      try (CloseableIterator<NodeExecution> iterator = nodeExecutionService.fetchChildrenNodeExecutionsIterator(
               resumeMetadata.getNodeExecutionUuid(), NodeProjectionUtils.fieldsForResponseNotifyData)) {
        while (iterator.hasNext()) {
          NodeExecution next = iterator.next();
          // Only oldRetry false nodes to be added
          if (Boolean.FALSE.equals(next.getOldRetry())) {
            childExecutions.add(next);
          }
        }
      }

      // TODO(archit): Make outcome service to be paginated
      Map<String, List<StepOutcomeRef>> refMap = pmsOutcomeService.fetchOutcomeRefs(
          childExecutions.stream().map(NodeExecution::getUuid).collect(Collectors.toList()));
      for (NodeExecution ce : childExecutions) {
        StepResponseNotifyData notifyData = StepResponseNotifyData.builder()
                                                .nodeUuid(ce.getNodeId())
                                                .identifier(ce.getIdentifier())
                                                .nodeExecutionId(ce.getUuid())
                                                .status(ce.getStatus())
                                                .failureInfo(ce.getFailureInfo())
                                                .stepOutcomeRefs(refMap.get(ce.getUuid()))
                                                .adviserResponse(ce.getAdviserResponse())
                                                .nodeExecutionEndTs(ce.getEndTs())
                                                .build();
        byteResponseMap.put(ce.getUuid(),
            ResponseDataProto.newBuilder()
                .setResponse(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(notifyData)))
                .setUsingKryoWithoutReference(false)
                .build());
      }
      return byteResponseMap;
    }

    return response;
  }

  @VisibleForTesting
  boolean accumulationRequired(ResumeMetadata metadata) {
    ExecutionMode mode = metadata.getMode();
    if (mode != ExecutionMode.CHILD && mode != ExecutionMode.CHILD_CHAIN) {
      return false;
    } else if (mode == ExecutionMode.CHILD) {
      return true;
    } else {
      ChildChainExecutableResponse lastChildChainExecutableResponse =
          Preconditions.checkNotNull(Objects.requireNonNull(metadata.getLatestExecutableResponse()).getChildChain());
      return !lastChildChainExecutableResponse.getSuspend();
    }
  }
}
