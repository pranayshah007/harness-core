/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azureartifacts;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDC)
public class AzureArtifactsDelegateRequest implements ArtifactSourceDelegateRequest {
  /**
   * Package Name in repos need to be referenced.
   */
  String packageName;

  /**
   * Package Name in repos need to be referenced.
   */
  String registryUrl;

  /**
   * Package Type
   */
  String packageType;

  /**
   * Exact Version of the artifact
   */
  String version;

  /**
   * Version Regex
   */
  String versionRegex;

  /**
   * Project of the package if any
   */
  String project;

  /**
   * Scope
   */
  String scope;

  /**
   * Feed
   */
  String feed;

  /**
   * Azure Artifacts Connector
   */
  AzureArtifactsConnectorDTO azureArtifactsConnectorDTO;

  /**
   * Encrypted details for decrypting.
   */
  List<EncryptedDataDetail> encryptedDataDetails;

  /**
   * Artifact Source type.
   */
  ArtifactSourceType sourceType;

  /**
   * Connector Reference.
   */
  String connectorRef;

  /**
   * Package Id
   */
  String packageId;

  /**
   * Delegate Selectors.
   */
  List<String> delegateSelectors;

  public Set<String> getDelegateSelectors() {
    Set<String> combinedDelegateSelectors = new HashSet<>();

    if (azureArtifactsConnectorDTO != null && azureArtifactsConnectorDTO.getDelegateSelectors() != null) {
      combinedDelegateSelectors.addAll(azureArtifactsConnectorDTO.getDelegateSelectors());
    }

    if (delegateSelectors != null) {
      combinedDelegateSelectors.addAll(delegateSelectors);
    }

    return combinedDelegateSelectors;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));

    populateDelegateSelectorCapability(capabilities, azureArtifactsConnectorDTO.getDelegateSelectors());

    capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        azureArtifactsConnectorDTO.getAzureArtifactsUrl().endsWith("/")
            ? azureArtifactsConnectorDTO.getAzureArtifactsUrl()
            : azureArtifactsConnectorDTO.getAzureArtifactsUrl().concat("/"),
        maskingEvaluator));

    return capabilities;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.AZURE_ARTIFACTS;
  }
}
