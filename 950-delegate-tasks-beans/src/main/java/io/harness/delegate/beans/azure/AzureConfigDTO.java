/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.azure;

import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.SecretRefData;
import io.harness.expression.ExpressionEvaluator;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(exclude = "key")
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("AzureConfig")
public class AzureConfigDTO implements DecryptableEntity, ExecutionCapabilityDemander {
  private static final String AZURE_URL = "https://azure.microsoft.com/";
  private String clientId;
  private String tenantId;
  private AzureEnvironmentType azureEnvironmentType;

  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData key;

  @Builder
  public AzureConfigDTO(
      String clientId, String tenantId, SecretRefData key, AzureEnvironmentType azureEnvironmentType) {
    this.clientId = clientId;
    this.tenantId = tenantId;
    this.key = key;
    this.azureEnvironmentType = azureEnvironmentType;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(AZURE_URL, maskingEvaluator));
  }
}
