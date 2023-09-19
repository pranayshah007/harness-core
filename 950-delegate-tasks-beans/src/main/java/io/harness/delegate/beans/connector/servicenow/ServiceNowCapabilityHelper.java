/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ServiceNowCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilitiesForValidation(
      ExpressionEvaluator maskingEvaluator, ServiceNowConnectorDTO serviceNowConnectorDTO) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    String serviceNowUrl = serviceNowConnectorDTO.getServiceNowUrl();
    capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        serviceNowUrl.endsWith("/") ? serviceNowUrl : serviceNowUrl.concat("/"), maskingEvaluator));
    populateDelegateSelectorCapability(capabilityList, serviceNowConnectorDTO.getDelegateSelectors(), serviceNowConnectorDTO.getConnectorType());
    return capabilityList;
  }

  public static List<ExecutionCapability> generateDelegateCapabilities(ServiceNowConnectorDTO capabilityDemander,
      List<EncryptedDataDetail> encryptedDataDetails, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (capabilityDemander != null) {
      executionCapabilities.addAll(
          fetchRequiredExecutionCapabilitiesForValidation(maskingEvaluator, capabilityDemander));
    }
    if (isEmpty(encryptedDataDetails)) {
      return executionCapabilities;
    }

    executionCapabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
        encryptedDataDetails, maskingEvaluator));
    return executionCapabilities;
  }
}
