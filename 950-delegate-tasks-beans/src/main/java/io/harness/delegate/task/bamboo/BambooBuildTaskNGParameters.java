/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.bamboo;

import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.BambooCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.BambooConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BambooBuildTaskNGParameters implements TaskParameters, ExecutionCapabilityDemander {
  BambooConnectorDTO bambooConnectorDTO;
  BambooConfig bambooConfig;
  List<EncryptedDataDetail> encryptionDetails;
  List<String> delegateSelectors;
  String planName;

  Map<String, String> jobParameter;

  public Set<String> getDelegateSelectors() {
    Set<String> combinedDelegateSelectors = new HashSet<>();
    if (bambooConnectorDTO != null && bambooConnectorDTO.getDelegateSelectors() != null) {
      combinedDelegateSelectors.addAll(bambooConnectorDTO.getDelegateSelectors());
    }
    if (delegateSelectors != null) {
      combinedDelegateSelectors.addAll(delegateSelectors);
    }
    return combinedDelegateSelectors;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return BambooCapabilityGenerator.generateDelegateCapabilities(
        bambooConnectorDTO, encryptionDetails, maskingEvaluator);
  }
}
