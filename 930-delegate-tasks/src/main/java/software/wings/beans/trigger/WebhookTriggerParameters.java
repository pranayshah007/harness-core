/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class WebhookTriggerParameters implements TaskParameters, ExecutionCapabilityDemander {
  private String eventPayload;
  private WebhookSource webhookSource;
  private String hashedPayload;
  private EncryptedDataDetail encryptedDataDetail;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (encryptedDataDetail != null && encryptedDataDetail.getEncryptionConfig() != null) {
      return EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(
          encryptedDataDetail.getEncryptionConfig(), null);
    }
    return new ArrayList<>();
  }
}
