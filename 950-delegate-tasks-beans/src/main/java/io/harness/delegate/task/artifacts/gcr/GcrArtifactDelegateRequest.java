/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gcr;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

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
@OwnedBy(HarnessTeam.PIPELINE)
public class GcrArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  /**
   * Images in repos need to be referenced via a path.
   */
  String imagePath;
  /**
   * Tag refers to exact tag number.
   */
  String tag;
  /** Tag regex is used to get latest build from builds matching regex. */
  String tagRegex;
  /** List of buildNumbers/tags */
  List<String> tagsList;
  /** RegistryHostName */
  String registryHostname;
  String connectorRef;
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
        populateDelegateSelectorCapability(capabilities, gcpConnectorDTO.getDelegateSelectors(),gcpConnectorDTO.getConnectorType());
        capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            "https://" + registryHostname, maskingEvaluator));
      } else {
        throw new UnknownEnumTypeException(
            "Gcr Credential Type", String.valueOf(gcpConnectorDTO.getCredential().getGcpCredentialType()));
      }
    }
    return capabilities;
  }
}
