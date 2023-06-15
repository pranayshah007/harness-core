/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ecr;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.PIPELINE)
public class EcrArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  /**
   * RegistryId of the image.
   */
  String registryId;
  /**
   * Images in repos need to be referenced via a path.
   */
  String imagePath;
  /**
   * Tag refers to exact tag number.
   */
  String tag;
  /**
   * Tag regex is used to get latest build from builds matching regex.
   */
  String tagRegex;
  /**
   * List of buildNumbers/tags
   */
  List<String> tagsList;
  /**
   * Region
   */
  String region;
  String connectorRef;
  /** aws Connector*/
  AwsConnectorDTO awsConnectorDTO;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;

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
            "Ecr Credential Type", String.valueOf(awsConnectorDTO.getCredential().getAwsCredentialType()));
      }
    }
    return capabilities;
  }
}
