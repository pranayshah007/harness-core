/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gar;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDC)
public class GarDelegateRequest implements ArtifactSourceDelegateRequest {
  String version;
  String versionRegex;
  String identifier;
  String region;
  String project;
  String repositoryName;
  String pkg;
  String connectorRef;
  int maxBuilds;
  /** Gcp Connector*/
  GcpConnectorDTO gcpConnectorDTO;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));
    if (gcpConnectorDTO.getCredential() != null) {
      if (gcpConnectorDTO.getCredential().getGcpCredentialType() == GcpCredentialType.INHERIT_FROM_DELEGATE) {
        populateDelegateSelectorCapability(capabilities, gcpConnectorDTO.getDelegateSelectors(), gcpConnectorDTO.getConnectorType());
      } else if (gcpConnectorDTO.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
        String registryHostname = String.format("%s-docker.pkg.dev", region);
        populateDelegateSelectorCapability(capabilities, gcpConnectorDTO.getDelegateSelectors(), gcpConnectorDTO.getConnectorType());
        capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            "https://" + registryHostname, maskingEvaluator));
      } else {
        throw new UnknownEnumTypeException(
            "Gcr Credential Type", String.valueOf(gcpConnectorDTO.getCredential().getGcpCredentialType()));
      }
    }
    return capabilities;
  }
  @Override
  public ArtifactSourceType getSourceType() {
    return GOOGLE_ARTIFACT_REGISTRY;
  }
}
