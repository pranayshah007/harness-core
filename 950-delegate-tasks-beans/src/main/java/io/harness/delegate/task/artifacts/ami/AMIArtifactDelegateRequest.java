/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ami;
import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Value
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDC)
public class AMIArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  /**
   * Region
   */
  String region;

  /**
   * Tags
   */
  Map<String, List<String>> tags;

  /**
   * Filters
   */
  Map<String, String> filters;

  /**
   * Exact Version of the artifact
   */
  String version;

  /**
   * Version Regex
   */
  String versionRegex;

  /**
   * AWS Connector
   */
  AwsConnectorDTO awsConnectorDTO;

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
   * Delegate Selectors.
   */
  List<String> delegateSelectors;

  public Set<String> getDelegateSelectors() {
    Set<String> combinedDelegateSelectors = new HashSet<>();

    if (awsConnectorDTO != null && awsConnectorDTO.getDelegateSelectors() != null) {
      combinedDelegateSelectors.addAll(awsConnectorDTO.getDelegateSelectors());
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
    if (awsConnectorDTO.getCredential() != null) {
      if (awsConnectorDTO.getCredential().getAwsCredentialType() == AwsCredentialType.INHERIT_FROM_DELEGATE
          || awsConnectorDTO.getCredential().getAwsCredentialType() == AwsCredentialType.MANUAL_CREDENTIALS
          || awsConnectorDTO.getCredential().getAwsCredentialType() == AwsCredentialType.IRSA) {
        populateDelegateSelectorCapability(capabilities, awsConnectorDTO.getDelegateSelectors());
        if (awsConnectorDTO.getCredential().getAwsCredentialType() == AwsCredentialType.MANUAL_CREDENTIALS) {
          capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              AwsInternalConfig.AWS_URL, maskingEvaluator));
        }
      } else {
        throw new UnknownEnumTypeException(
            "AWS Credential Type", String.valueOf(awsConnectorDTO.getCredential().getAwsCredentialType()));
      }
    }
    return capabilities;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.AMI;
  }
}
