/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class AzureBlueprintTaskNGParameters extends AzureTaskNGParameters {
  @Expression(ALLOW_SECRETS) private String blueprintJson;
  @Expression(ALLOW_SECRETS) private Map<String, String> artifacts;
  @Expression(ALLOW_SECRETS) private String assignmentJson;
  private final String assignmentName;
  private final String scope;

  @Builder
  public AzureBlueprintTaskNGParameters(String accountId, AzureARMTaskType taskType, AzureConnectorDTO connectorDTO,
      long timeoutInMs, String blueprintJson, Map<String, String> artifacts, String assignmentJson,
      String assignmentName, List<EncryptedDataDetail> encryptedDataDetailList, String scope) {
    super(accountId, taskType, connectorDTO, encryptedDataDetailList, timeoutInMs);
    this.blueprintJson = blueprintJson;
    this.artifacts = artifacts;
    this.assignmentJson = assignmentJson;
    this.assignmentName = assignmentName;
    this.scope = scope;
  }
}
