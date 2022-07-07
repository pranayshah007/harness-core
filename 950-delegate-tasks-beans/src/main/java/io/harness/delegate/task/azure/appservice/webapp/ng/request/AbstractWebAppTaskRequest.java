/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@OwnedBy(CDP)
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractWebAppTaskRequest implements AzureWebAppTaskRequest {
  @Getter @Setter @NonNull String accountId;
  @Getter @Setter private CommandUnitsProgress commandUnitsProgress;
  @Getter private AzureWebAppInfraDelegateConfig infrastructure;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (getInfrastructure() != null) {
      capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
          infrastructure.getAzureConnectorDTO(), maskingEvaluator));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          infrastructure.getEncryptionDataDetails(), maskingEvaluator));
    }

    populateRequestCapabilities(capabilities, maskingEvaluator);
    return capabilities;
  }

  @Override
  public Map<DecryptableEntity, List<EncryptedDataDetail>> fetchDecryptionDetails() {
    Map<DecryptableEntity, List<EncryptedDataDetail>> decryptionDetails = new LinkedHashMap<>();
    if (infrastructure != null) {
      infrastructure.getDecryptableEntities().forEach(
          decryptableEntity -> decryptionDetails.put(decryptableEntity, infrastructure.getEncryptionDataDetails()));
    }

    populateDecryptionDetails(decryptionDetails);
    return decryptionDetails;
  }

  protected void populateDecryptionDetails(Map<DecryptableEntity, List<EncryptedDataDetail>> decryptionDetails) {
    // used for request specific additional decryption details
  }

  protected void populateRequestCapabilities(
      List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    // used for request specific additional capabilities
  }
}
