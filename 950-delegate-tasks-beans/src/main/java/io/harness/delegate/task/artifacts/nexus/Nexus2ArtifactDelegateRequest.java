/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.nexus;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * DTO object to be passed to delegate tasks.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDP)
public class Nexus2ArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  /** Host from where to pull the images */
  String artifactRepositoryUrl;
  /** Nexus repo name. */
  String repositoryName;
  /** Nexus repository port. */
  String repositoryPort;
  /** Images in repos need to be referenced via a path. */
  String artifactPath;
  /** Nexus repository format type. */
  String repositoryFormat;
  /** Tag refers to exact tag number. */
  String tag;
  /** Tag regex is used to get latest build from builds matching regex. */
  String tagRegex;
  String connectorRef;
  /** Nexus Connector*/
  NexusConnectorDTO nexusConnectorDTO;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;
  String groupId;
  String artifactName;
  String extension;
  String classifier;
  String packageName;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));
    populateDelegateSelectorCapability(capabilities, nexusConnectorDTO.getDelegateSelectors(), nexusConnectorDTO.getConnectorType());
    capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        nexusConnectorDTO.getNexusServerUrl(), maskingEvaluator));
    return capabilities;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.NEXUS2_REGISTRY;
  }
}
