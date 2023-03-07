/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud.cleanup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.terraformcloud.TerraformCloudCapabilityHelper;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudCleanupTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  @NonNull TerraformCloudConnectorDTO terraformCloudConnectorDTO;
  @NonNull List<EncryptedDataDetail> encryptionDetails;
  @NonNull String runId;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return TerraformCloudCapabilityHelper.fetchRequiredExecutionCapabilities(
        terraformCloudConnectorDTO, maskingEvaluator);
  }
}
