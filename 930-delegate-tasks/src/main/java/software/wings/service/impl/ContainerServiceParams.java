/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.utils.CapablityUtility;
import software.wings.delegatetasks.validation.capabilities.ClusterMasterUrlValidationCapability;
import software.wings.settings.SettingValue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@Slf4j
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class ContainerServiceParams implements ExecutionCapabilityDemander {
  private SettingAttribute settingAttribute;
  private List<EncryptedDataDetail> encryptionDetails;
  private String containerServiceName;
  private String clusterName;
  private String namespace;
  private String region;
  private String subscriptionId;
  private String resourceGroup;
  private String masterUrl;
  private Set<String> containerServiceNames;
  private boolean cloudCostEnabled;

  private String releaseName;

  public boolean isKubernetesClusterConfig() {
    if (settingAttribute == null) {
      return false;
    }

    SettingValue value = settingAttribute.getValue();

    return value instanceof AzureConfig || value instanceof GcpConfig || value instanceof KubernetesClusterConfig;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (settingAttribute == null) {
      return emptyList();
    }
    SettingValue value = settingAttribute.getValue();

    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (value instanceof RancherConfig) {
      return CapablityUtility.generateDelegateCapabilities(value, encryptionDetails, maskingEvaluator);
    } else if (value instanceof AwsConfig) {
      return value.fetchRequiredExecutionCapabilities(maskingEvaluator);
    } else if (value instanceof KubernetesClusterConfig
        && ((KubernetesClusterConfig) value).isUseKubernetesDelegate()) {
      executionCapabilities.add(
          SelectorCapability.builder().selectors(((KubernetesClusterConfig) value).getDelegateSelectors()).build());
    } else {
      if ("None".equals(clusterName)) {
        executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            "https://container.googleapis.com/", null));
      } else {
        if (isEmpty(masterUrl)) {
          executionCapabilities.add(
              ClusterMasterUrlValidationCapability.builder().containerServiceParams(this).build());
        } else {
          executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              masterUrl, maskingEvaluator));
        }
      }
    }
    return executionCapabilities;
  }
}