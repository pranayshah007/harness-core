/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO object to be passed to delegate tasks.
 */

@OwnedBy(HarnessTeam.CDP)
public interface ArtifactoryBaseArtifactDelegateRequest extends ArtifactSourceDelegateRequest {
  /** Repository name */
  String getRepositoryName();
  String getArtifactPath();
  /** Repository format - package type */
  String getRepositoryFormat();
  String getConnectorRef();
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> getEncryptedDataDetails();
  /** Artifactory Connector*/
  ArtifactoryConnectorDTO getArtifactoryConnectorDTO();

  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            getEncryptedDataDetails(), maskingEvaluator));
    populateDelegateSelectorCapability(capabilities, getArtifactoryConnectorDTO().getDelegateSelectors(),getArtifactoryConnectorDTO().getConnectorType());
    capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        getArtifactoryConnectorDTO().getArtifactoryServerUrl(), maskingEvaluator));
    return capabilities;
  }
}