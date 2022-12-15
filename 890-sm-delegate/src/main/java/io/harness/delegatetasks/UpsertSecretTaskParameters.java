/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpsertSecretTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  private final String name;
  private final String plaintext;
  private final UpsertSecretTaskType taskType;
  private final EncryptedRecord existingRecord;
  private final EncryptionConfig encryptionConfig;
  private final AdditionalMetadata additionalMetadata;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return ((SecretManagerConfig) encryptionConfig).fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}
