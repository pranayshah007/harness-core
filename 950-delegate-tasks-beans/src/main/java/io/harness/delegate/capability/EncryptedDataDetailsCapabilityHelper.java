/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.capability;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.common.EncryptionConfig;
import io.harness.security.encryption.common.EncryptionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EncryptedDataDetailsCapabilityHelper {
  public static List<ExecutionCapability> fetchExecutionCapabilitiesForSecretManagers(
      Collection<EncryptionConfig> encryptionConfigs, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    encryptionConfigs.forEach(encryptionConfig -> {
      List<ExecutionCapability> encryptionConfigExecutionCapabilities =
          fetchExecutionCapabilityForSecretManager(encryptionConfig, maskingEvaluator);
      executionCapabilities.addAll(encryptionConfigExecutionCapabilities);
    });

    return executionCapabilities;
  }

  public static List<ExecutionCapability> fetchExecutionCapabilitiesForEncryptedDataDetails(
      List<EncryptedDataDetail> encryptedDataDetails, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (isEmpty(encryptedDataDetails)) {
      return executionCapabilities;
    }
    return fetchExecutionCapabilitiesForSecretManagers(
        fetchEncryptionConfigsMapFromEncryptedDataDetails(encryptedDataDetails).values(), maskingEvaluator);
  }

  public static List<ExecutionCapability> fetchExecutionCapabilityForSecretManager(
      @NotNull EncryptionConfig encryptionConfig, ExpressionEvaluator maskingEvaluator) {
    if (encryptionConfig instanceof ExecutionCapabilityDemander) {
      return ((ExecutionCapabilityDemander) encryptionConfig).fetchRequiredExecutionCapabilities(maskingEvaluator);
    } else if (isNotEmpty(encryptionConfig.getEncryptionServiceUrl())) {
      return new ArrayList<>(
          Collections.singleton(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              encryptionConfig.getEncryptionServiceUrl(), maskingEvaluator)));
    }
    return new ArrayList<>();
  }

  public static Map<String, EncryptionConfig> fetchEncryptionConfigsMapFromEncryptedDataDetails(
      List<EncryptedDataDetail> encryptedDataDetails) {
    Map<String, EncryptionConfig> encryptionConfigsMap = new HashMap<>();
    if (isEmpty(encryptedDataDetails)) {
      return encryptionConfigsMap;
    }
    List<EncryptedDataDetail> nonLocalEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> encryptedDataDetail.getEncryptedData().getEncryptionType() != EncryptionType.LOCAL)
            .collect(Collectors.toList());
    if (isNotEmpty(nonLocalEncryptedDetails)) {
      nonLocalEncryptedDetails.forEach(nonLocalEncryptedDetail
          -> encryptionConfigsMap.put(
              nonLocalEncryptedDetail.getEncryptionConfig().getUuid(), nonLocalEncryptedDetail.getEncryptionConfig()));
    }
    return encryptionConfigsMap;
  }
}
