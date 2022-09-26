/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.vmss.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.azure.vmss.ng.AzureVMSSInfraDelegateConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

@Data
@OwnedBy(CDP)
public abstract class AbstractAzureVMSSTaskRequest implements AzureVMSSTaskRequest {
  private int timeout;
  @Expression(ALLOW_SECRETS) private AzureVMSSInfraDelegateConfig infrastructure;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (infrastructure != null) {
      capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
          infrastructure.getAzureConnectorDTO(), maskingEvaluator));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          infrastructure.getEncryptionDataDetails(), maskingEvaluator));
    }

    populateExecutionCapabilities(capabilities, maskingEvaluator);
    return capabilities;
  }

  @Override
  public List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> fetchDecryptionDetails() {
    List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails = new ArrayList<>();
    if (infrastructure != null) {
      for (DecryptableEntity decryptableEntity : infrastructure.fetchDecryptableEntities()) {
        decryptionDetails.add(Pair.of(decryptableEntity, infrastructure.getEncryptionDataDetails()));
      }
    }

    populateDecryptionDetails(decryptionDetails);
    return decryptionDetails;
  }

  protected abstract void populateDecryptionDetails(
      List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails);

  protected abstract void populateExecutionCapabilities(
      List<ExecutionCapability> executionCapabilities, ExpressionEvaluator maskingEvaluator);
}
