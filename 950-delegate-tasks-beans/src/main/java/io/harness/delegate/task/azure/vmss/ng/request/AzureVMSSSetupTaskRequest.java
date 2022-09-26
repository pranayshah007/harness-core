/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.vmss.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.azure.vmss.ng.AzureVMAuthentication;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.tuple.Pair;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class AzureVMSSSetupTaskRequest extends AbstractAzureVMSSTaskRequest {
  private AzureVMAuthentication vmAuthentication;
  private CommandUnitsProgress commandUnitsProgress;

  @Override
  public AzureVMSSRequestType getRequestType() {
    return AzureVMSSRequestType.AZURE_VMSS_SETUP;
  }

  @Override
  protected void populateDecryptionDetails(List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails) {
    if (vmAuthentication != null) {
      decryptionDetails.add(Pair.of(vmAuthentication, vmAuthentication.getEncryptionDetails()));
    }
  }

  @Override
  protected void populateExecutionCapabilities(
      List<ExecutionCapability> executionCapabilities, ExpressionEvaluator maskingEvaluator) {
    if (vmAuthentication != null) {
      executionCapabilities.addAll(
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
              vmAuthentication.getEncryptionDetails(), maskingEvaluator));
    }
  }
}
