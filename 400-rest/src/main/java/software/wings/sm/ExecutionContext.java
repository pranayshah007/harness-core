/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.beans.WorkflowType;
import io.harness.context.ContextElementType;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.api.InfraMappingElement;
import software.wings.api.ServiceElement;
import software.wings.api.instancedetails.InstanceApiResponse;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ErrorStrategy;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry.SweepingOutputInquiryBuilder;
import software.wings.settings.SettingValue;

import java.util.List;
import java.util.Map;

/**
 * The Interface ExecutionContext.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(_957_CG_BEANS)
public interface ExecutionContext {
  Object evaluateExpression(String expression);

  Object evaluateExpression(String expression, StateExecutionContext stateExecutionContext);

  <T extends StateExecutionData> T getStateExecutionData();

  boolean isRetry();

  int retryCount();

  Map<String, Object> asMap();

  void resetPreparedCache();

  String renderExpression(String expression);

  /**
   * Renders provided expression and masks result if result is a secret
   *
   * @param expression the expression to be resolved and masked if value is a secret
   * @return resolved expression for non-secret values or mask for secrets
   */
  String renderExpressionSecured(String expression);

  String renderExpression(String expression, StateExecutionContext stateExecutionContext);

  List<String> renderExpressionList(List<String> expressions);

  List<String> renderExpressionList(List<String> expressions, String separator);

  <T extends ContextElement> T getContextElement();

  <T extends ContextElement> T getContextElement(ContextElementType contextElementType);

  <T extends ContextElement> T getContextElement(ContextElementType contextElementType, String name);

  <T extends ContextElement> List<T> getContextElementList(ContextElementType contextElementType);

  ErrorStrategy getErrorStrategy();

  Application getApp();

  Application fetchRequiredApp();

  Environment fetchRequiredEnvironment();

  String getWorkflowExecutionId();

  String getWorkflowId();

  String getWorkflowExecutionName();

  WorkflowType getWorkflowType();

  OrchestrationWorkflowType getOrchestrationWorkflowType();

  String getStateExecutionInstanceId();

  String getPipelineStageElementId();

  int getPipelineStageParallelIndex();

  String getPipelineStageName();

  String getAppId();

  String getAccountId();

  String getStateExecutionInstanceName();

  Map<String, Object> getServiceVariables();

  Map<String, String> getSafeDisplayServiceVariables();

  <T extends SettingValue> T getGlobalSettingValue(String accountId, String settingId);

  SweepingOutputInstanceBuilder prepareSweepingOutputBuilder(SweepingOutputInstance.Scope sweepingOutputScope);

  SweepingOutputInquiryBuilder prepareSweepingOutputInquiryBuilder();

  InfraMappingElement fetchInfraMappingElement();

  String fetchInfraMappingId();

  ServiceElement fetchServiceElement();

  AutoLogContext autoLogContext();

  InstanceApiResponse renderExpressionsForInstanceDetails(String expression, boolean newInstancesOnly);

  InstanceApiResponse renderExpressionsForInstanceDetailsForWorkflow(String expression, boolean newInstancesOnly);

  String appendStateExecutionId(String str);

  String getEnvType();

  /*
   * The below method assumes that the Workflow Phases are statically generated and
   * hence should not be used in Rolling and similar workflows.
   */
  boolean isLastPhase(boolean rollback);
}
