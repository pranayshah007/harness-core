/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jenkins;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.JenkinsCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JenkinsBuildTaskNGParameters implements TaskParameters, ExecutionCapabilityDemander {
  JenkinsConnectorDTO jenkinsConnectorDTO;
  JenkinsInternalConfig jenkinsInternalConfig;
  List<EncryptedDataDetail> encryptionDetails;
  List<String> delegateSelectors;
  String jobName;

  Map<String, String> jobParameter;
  boolean unstableStatusAsSuccess;
  boolean captureEnvironmentVariable;

  public Set<String> getDelegateSelectors() {
    Set<String> combinedDelegateSelectors = new HashSet<>();
    if (jenkinsConnectorDTO != null && jenkinsConnectorDTO.getDelegateSelectors() != null) {
      combinedDelegateSelectors.addAll(jenkinsConnectorDTO.getDelegateSelectors());
    }
    if (delegateSelectors != null) {
      combinedDelegateSelectors.addAll(delegateSelectors);
    }
    return combinedDelegateSelectors;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return JenkinsCapabilityGenerator.generateDelegateCapabilities(
        jenkinsConnectorDTO, encryptionDetails, maskingEvaluator);
  }
}
