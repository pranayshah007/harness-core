/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudsource;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
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
@OwnedBy(HarnessTeam.PIPELINE)
public class GoogleCloudSourceArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  /** refers to GCP Project*/
  String project;
  /** refers to GCS repository*/
  String repository;
  /** refers to sourceDirectory in GCS repository*/
  String sourceDirectory;
  /** Gcp Connector*/
  GcpConnectorDTO gcpConnectorDTO;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;
  String connectorRef;
  GoogleCloudSourceFetchType googleCloudSourceFetchType;
  String branch;
  String commitId;
  String tag;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));
    capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(gcpConnectorDTO, maskingEvaluator));
    if (gcpConnectorDTO.getCredential() != null) {
      if (gcpConnectorDTO.getCredential().getGcpCredentialType() != GcpCredentialType.INHERIT_FROM_DELEGATE
          && gcpConnectorDTO.getCredential().getGcpCredentialType() != GcpCredentialType.MANUAL_CREDENTIALS) {
        throw new UnknownEnumTypeException(
            "Gcr Credential Type", String.valueOf(gcpConnectorDTO.getCredential().getGcpCredentialType()));
      }
      populateDelegateSelectorCapability(capabilities, gcpConnectorDTO.getDelegateSelectors(), gcpConnectorDTO.getConnectorType());
    }
    return capabilities;
  }
}
