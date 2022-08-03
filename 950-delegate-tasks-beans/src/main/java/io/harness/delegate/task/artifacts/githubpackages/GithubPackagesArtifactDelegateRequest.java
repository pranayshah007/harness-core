/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
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

public class GithubPackagesArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  /** Package Name in repos need to be referenced. */
  String packageName;
  /** Package Type */
  String packageType;
  /** org of the package if any*/
  String org;
  /** Exact Version of the artifact*/
  String version;
  /** Version Regex*/
  String versionRegex;
  /** Github Connector*/
  GithubConnectorDTO githubConnectorDTO;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));
    if (githubConnectorDTO.getAuthentication().getCredentials() != null) {
      if (githubConnectorDTO.getAuthentication().getAuthType() == GitAuthType.HTTP
          || githubConnectorDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
        populateDelegateSelectorCapability(capabilities, githubConnectorDTO.getDelegateSelectors());
        if (githubConnectorDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
          capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              githubConnectorDTO.getGitConnectionUrl(), maskingEvaluator));
        }
      } else {
                throw new UnknownEnumTypeException(
                        "Github Credential Type", String.valueOf(githubConnectorDTO.getAuthentication().getAuthType());
      }
    }
    return capabilities;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.GITHUB_PACKAGES;
  }
}
