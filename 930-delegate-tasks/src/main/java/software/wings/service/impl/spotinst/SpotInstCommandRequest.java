/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class SpotInstCommandRequest implements TaskParameters, ExecutionCapabilityDemander {
  private AwsConfig awsConfig;
  private SpotInstConfig spotInstConfig;
  private List<EncryptedDataDetail> awsEncryptionDetails;
  private List<EncryptedDataDetail> spotinstEncryptionDetails;
  private SpotInstTaskParameters spotInstTaskParameters;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    Set<ExecutionCapability> executionCapabilities = new HashSet<>();
    executionCapabilities.addAll(
        CapabilityHelper.generateDelegateCapabilities(awsConfig, awsEncryptionDetails, maskingEvaluator));
    executionCapabilities.addAll(
        CapabilityHelper.generateDelegateCapabilities(spotInstConfig, spotinstEncryptionDetails, maskingEvaluator));
    return new ArrayList<>(executionCapabilities);
  }
}
