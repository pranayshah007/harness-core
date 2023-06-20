/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.TimeoutParameters;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class WaitForExecutionInputHelper {
  private static final Long MILLIS_IN_SIX_MONTHS = 86400 * 30 * 6L;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private ExecutionInputService executionInputService;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Inject private KryoSerializer kryoSerializer;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  public boolean waitForExecutionInput(Ambiance ambiance, String nodeExecutionId, PlanNode node) {
    if (!pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_EXECUTION_INPUT)
        || EmptyPredicate.isEmpty(node.getExecutionInputTemplate())) {
      return false;
    }
    // If instance is already there then that means we have already processed the user input.
    if (executionInputService.isPresent(nodeExecutionId)) {
      return false;
    }
    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        planExecutionMetadataService.findByPlanExecutionId(ambiance.getPlanExecutionId());
    if (planExecutionMetadataOptional.isPresent()) {
      Long currentTime = System.currentTimeMillis();
      String inputInstanceId = UUIDGenerator.generateUuid();
      EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(ambiance);
      JsonNode fieldJsonNode = YamlNode.getNodeYaml(
          YamlUtils.readYamlTree(planExecutionMetadataOptional.get().getYaml()).getNode(), ambiance.getLevelsList());
      // Resolve any expression in fieldYaml that can be resolved so far.
      fieldJsonNode = (JsonNode) pmsEngineExpressionService.resolve(
          ambiance, fieldJsonNode, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      long timeout = 0;
      if (EmptyPredicate.isNotEmpty(node.getTimeoutObtainments())) {
        // We take the last timeout added as timeout for the step.
        TimeoutParameters timeoutParameters = OrchestrationUtils.buildTimeoutParameters(
            kryoSerializer, evaluator, node.getTimeoutObtainments().get(node.getTimeoutObtainments().size() - 1));
        timeout = timeoutParameters.getTimeoutMillis();
      }

      WaitForExecutionInputCallback waitForExecutionInputCallback = WaitForExecutionInputCallback.builder()
                                                                        .nodeExecutionId(nodeExecutionId)
                                                                        .ambiance(ambiance)
                                                                        .inputInstanceId(inputInstanceId)
                                                                        .build();
      waitNotifyEngine.waitForAllOnInList(publisherName, waitForExecutionInputCallback,
          Lists.newArrayList(inputInstanceId), Duration.ofMillis(timeout));
      executionInputService.save(ExecutionInputInstance.builder()
                                     .inputInstanceId(inputInstanceId)
                                     .nodeExecutionId(nodeExecutionId)
                                     .fieldYaml(YamlUtils.writeYamlString(fieldJsonNode))
                                     .template(node.getExecutionInputTemplate())
                                     .createdAt(currentTime)
                                     .validUntil(currentTime + MILLIS_IN_SIX_MONTHS)
                                     .build());
      // Updating the current node status. InputWaitingStatusUpdateHandler will update status of parent recursively.
      nodeExecutionService.updateStatusWithOps(
          nodeExecutionId, Status.INPUT_WAITING, null, EnumSet.noneOf(Status.class));
      return true;
    } else {
      log.error("Pipeline for planExecutionId {} is deleted or not does not exist.", ambiance.getPlanExecutionId());
      throw new InvalidRequestException(
          "Pipeline for planExecutionId " + ambiance.getPlanExecutionId() + " is deleted or not does not exist.");
    }
  }
}
