/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.terraformcloud;

import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.terraformcloud.TerraformCloudCapabilityHelper;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudTaskParams
    extends ConnectorTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  TerraformCloudTaskType terraformCloudTaskType;

  TerraformCloudConnectorDTO terraformCloudConnectorDTO;
  List<EncryptedDataDetail> encryptionDetails;

  String accountId;
  String entityId;

  String organization;
  String workspace;
  boolean discardPendingRuns;
  boolean exportJsonTfPlan;
  boolean policyOverride;
  PlanType planType;
  String terraformVersion;
  @Expression(ALLOW_SECRETS) Map<String, String> variables;
  @Expression(DISALLOW_SECRETS) List<String> targets;
  String runId;

  RollbackType rollbackType;
  String message;

  CommandUnitsProgress commandUnitsProgress;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return TerraformCloudCapabilityHelper.fetchRequiredExecutionCapabilities(
        terraformCloudConnectorDTO, maskingEvaluator);
  }
}
