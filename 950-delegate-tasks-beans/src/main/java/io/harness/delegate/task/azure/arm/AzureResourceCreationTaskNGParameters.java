/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AzureResourceCreationTaskNGParameters
    implements TaskParameters, ExecutionCapabilityDemander, ExpressionReflectionUtils.NestedAnnotationResolver {
  @NonNull String accountId;
  @NonNull AzureARMTaskType azureARMTaskType;
  @NonNull AzureConnectorDTO azureConnectorDTO;
  @NonNull List<EncryptedDataDetail> encryptedDataDetails;
  long timeoutInMs;
  CommandUnitsProgress commandUnitsProgress;
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return AzureCapabilityHelper.fetchRequiredExecutionCapabilities(azureConnectorDTO, maskingEvaluator);
  }

  public List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> fetchDecryptionDetails() {
    return Collections.emptyList();
  }

  void decryptFiles(
      AppSettingsFile settingsFile, List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails) {
    if (settingsFile != null && settingsFile.isEncrypted()) {
      decryptionDetails.add(Pair.of(settingsFile.getEncryptedFile(), settingsFile.getEncryptedDataDetails()));
    }
  }
}