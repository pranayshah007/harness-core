/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.NodeExecution.NodeExecutionKeys;

import com.google.common.collect.Sets;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class NodeProjectionUtils {
  public static final Set<String> withId = Sets.newHashSet(NodeExecutionKeys.uuid);

  public static final Set<String> withParentId = Sets.newHashSet(NodeExecutionKeys.parentId);

  public static final Set<String> withNextId = Sets.newHashSet(NodeExecutionKeys.nextId);

  public static final Set<String> fieldsForRetryInterruptHandler = Sets.newHashSet(
      NodeExecutionKeys.ambiance, NodeExecutionKeys.status, NodeExecutionKeys.oldRetry, NodeExecutionKeys.mode);

  public static final Set<String> fieldsForInterruptPropagatorHandler = Sets.newHashSet(
      NodeExecutionKeys.parentId, NodeExecutionKeys.status, NodeExecutionKeys.stepType, NodeExecutionKeys.mode);

  public static final Set<String> fieldsForAllChildrenExtractor =
      Sets.newHashSet(NodeExecutionKeys.parentId, NodeExecutionKeys.status, NodeExecutionKeys.stepType);

  // Can be used for InterruptMonitor as parentId is used for interruptMonitor to check parents
  public static final Set<String> fieldsForDiscontinuingNodes =
      Sets.newHashSet(NodeExecutionKeys.ambiance, NodeExecutionKeys.status, NodeExecutionKeys.executableResponses,
          NodeExecutionKeys.mode, NodeExecutionKeys.unitProgresses, NodeExecutionKeys.parentId);

  public static final Set<String> fieldsForInstrumentationHandler =
      Sets.newHashSet(NodeExecutionKeys.ambiance, NodeExecutionKeys.status);

  public static final Set<String> withAmbianceAndStatus =
      Sets.newHashSet(NodeExecutionKeys.ambiance, NodeExecutionKeys.status);

  public static final Set<String> withAmbiance = Sets.newHashSet(NodeExecutionKeys.ambiance);

  public static final Set<String> withStatus = Sets.newHashSet(NodeExecutionKeys.status);

  public static final Set<String> withExecutableResponses = Sets.newHashSet(NodeExecutionKeys.executableResponses);

  public static final Set<String> withStatusAndMode = Sets.newHashSet(NodeExecutionKeys.status, NodeExecutionKeys.mode);

  public static final Set<String> withStatusAndAdviserResponse =
      Sets.newHashSet(NodeExecutionKeys.status, NodeExecutionKeys.adviserResponse);

  public static final Set<String> fieldsForNodeUpdateObserver = Sets.newHashSet(
      NodeExecutionKeys.ambiance, NodeExecutionKeys.status, NodeExecutionKeys.endTs, NodeExecutionKeys.oldRetry);

  public static final Set<String> fieldsForNodeStatusUpdateObserver =
      Sets.newHashSet(NodeExecutionKeys.ambiance, NodeExecutionKeys.status, NodeExecutionKeys.resolvedParams,
          NodeExecutionKeys.endTs, NodeExecutionKeys.oldRetry, NodeExecutionKeys.timeoutInstanceIds);

  public static final Set<String> fieldsForResume =
      Sets.newHashSet(NodeExecutionKeys.status, NodeExecutionKeys.ambiance, NodeExecutionKeys.executableResponses,
          NodeExecutionKeys.mode, NodeExecutionKeys.resolvedParams, NodeExecutionKeys.module,
          NodeExecutionKeys.stepType, NodeExecutionKeys.originalNodeExecutionId);

  public static final Set<String> fieldsForInterruptEventPublish = Sets.newHashSet(NodeExecutionKeys.status,
      NodeExecutionKeys.ambiance, NodeExecutionKeys.executableResponses, NodeExecutionKeys.mode,
      NodeExecutionKeys.resolvedParams, NodeExecutionKeys.module, NodeExecutionKeys.stepType);

  public static final Set<String> fieldsForProgressEvent = Sets.newHashSet(NodeExecutionKeys.ambiance,
      NodeExecutionKeys.executableResponses, NodeExecutionKeys.mode, NodeExecutionKeys.resolvedParams,
      NodeExecutionKeys.module, NodeExecutionKeys.originalNodeExecutionId, NodeExecutionKeys.stepType);

  // NodeId is added to resolve expression within same step
  public static final Set<String> fieldsForExpressionEngine = Sets.newHashSet(NodeExecutionKeys.status,
      NodeExecutionKeys.ambiance, NodeExecutionKeys.mode, NodeExecutionKeys.startTs, NodeExecutionKeys.endTs,
      NodeExecutionKeys.parentId, NodeExecutionKeys.resolvedParams, NodeExecutionKeys.oldRetry,
      NodeExecutionKeys.nodeId, NodeExecutionKeys.retryIds);

  public static final Set<String> forFacilitation = Sets.newHashSet(NodeExecutionKeys.ambiance,
      NodeExecutionKeys.originalNodeExecutionId, NodeExecutionKeys.module, NodeExecutionKeys.resolvedParams);

  public static final Set<String> fieldsForResponseNotifyData = Sets.newHashSet(NodeExecutionKeys.identifier,
      NodeExecutionKeys.nodeId, NodeExecutionKeys.status, NodeExecutionKeys.adviserResponse,
      NodeExecutionKeys.failureInfo, NodeExecutionKeys.oldRetry, NodeExecutionKeys.endTs);

  public static final Set<String> fieldsForExecutionStrategy =
      Sets.newHashSet(NodeExecutionKeys.ambiance, NodeExecutionKeys.status, NodeExecutionKeys.adviserResponse,
          NodeExecutionKeys.failureInfo, NodeExecutionKeys.notifyId, NodeExecutionKeys.endTs);

  public static final Set<String> fieldsForIdentityStrategyStep = Sets.newHashSet(NodeExecutionKeys.identifier,
      NodeExecutionKeys.name, NodeExecutionKeys.nodeId, NodeExecutionKeys.status, NodeExecutionKeys.executableResponses,
      NodeExecutionKeys.ambiance, NodeExecutionKeys.oldRetry, NodeExecutionKeys.planNode, NodeExecutionKeys.parentId);

  public static final Set<String> fieldsForNodeExecutionDelete = Sets.newHashSet(NodeExecutionKeys.timeoutInstanceIds,
      NodeExecutionKeys.adviserTimeoutInstanceIds, NodeExecutionKeys.nodeId, NodeExecutionKeys.notifyId,
      NodeExecutionKeys.stepType, NodeExecutionKeys.executionInputConfigured);

  public static final Set<String> fieldsForIdentityNodeCreation =
      Sets.newHashSet(NodeExecutionKeys.planNode, NodeExecutionKeys.stepType, NodeExecutionKeys.uuid);

  public static final Set<String> fieldsForNodeAndAmbiance =
      Sets.newHashSet(NodeExecutionKeys.planNode, NodeExecutionKeys.ambiance, NodeExecutionKeys.nodeId);
}
