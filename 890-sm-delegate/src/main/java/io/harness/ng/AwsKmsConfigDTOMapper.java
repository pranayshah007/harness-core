/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorCredentialDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecAssumeIAMDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecAssumeSTSDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecManualConfigDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialType;
import io.harness.exception.InvalidRequestException;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsCredentialSpecConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsIamCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsManualCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsStsCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.BaseAwsKmsConfigDTO;
import io.harness.security.encryption.EncryptionType;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AwsKmsConfigDTOMapper {
  public static AwsKmsConfigDTO getAwsKmsConfigDTO(
      String accountIdentifier, ConnectorDTO connectorRequestDTO, AwsKmsConnectorDTO awsKmsConnectorDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    return AwsKmsConfigDTO.builder()
        .baseAwsKmsConfigDTO(buildBaseProperties(awsKmsConnectorDTO))
        .isDefault(false)
        .encryptionType(EncryptionType.KMS)

        .name(connector.getName())
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(connector.getOrgIdentifier())
        .projectIdentifier(connector.getProjectIdentifier())
        .tags(connector.getTags())
        .identifier(connector.getIdentifier())
        .description(connector.getDescription())
        .harnessManaged(awsKmsConnectorDTO.isHarnessManaged())
        .build();
  }

  public static AwsKmsConfigUpdateDTO getAwsKmsConfigUpdateDTO(
      ConnectorDTO connectorRequestDTO, AwsKmsConnectorDTO awsKmsConnectorDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    return AwsKmsConfigUpdateDTO.builder()
        .baseAwsKmsConfigDTO(buildBaseProperties(awsKmsConnectorDTO))
        .name(connector.getName())
        .isDefault(false)
        .encryptionType(EncryptionType.KMS)

        .tags(connector.getTags())
        .description(connector.getDescription())
        .build();
  }

  private static BaseAwsKmsConfigDTO buildBaseProperties(AwsKmsConnectorDTO awsKmsConnectorDTO) {
    char[] decryptedValue = awsKmsConnectorDTO.getKmsArn().getDecryptedValue();
    final String kmsArn = decryptedValue == null ? null : String.valueOf(decryptedValue);
    return BaseAwsKmsConfigDTO.builder()
        .region(awsKmsConnectorDTO.getRegion())
        .kmsArn(kmsArn)
        .credential(populateCredentials(awsKmsConnectorDTO))
        .credentialType(awsKmsConnectorDTO.getCredential().getCredentialType())
        .delegateSelectors(awsKmsConnectorDTO.getDelegateSelectors())
        .build();
  }

  private static AwsKmsCredentialSpecConfig populateCredentials(AwsKmsConnectorDTO awsKmsConnectorDTO) {
    AwsKmsConnectorCredentialDTO credential = awsKmsConnectorDTO.getCredential();
    AwsKmsCredentialType credentialType = credential.getCredentialType();
    AwsKmsCredentialSpecConfig awsKmsCredentialSpecConfig;
    switch (credentialType) {
      case MANUAL_CONFIG:
        awsKmsCredentialSpecConfig = buildManualConfig((AwsKmsCredentialSpecManualConfigDTO) credential.getConfig());
        break;
      case ASSUME_IAM_ROLE:
        awsKmsCredentialSpecConfig = buildIamConfig((AwsKmsCredentialSpecAssumeIAMDTO) credential.getConfig());
        break;
      case ASSUME_STS_ROLE:
        awsKmsCredentialSpecConfig = buildStsConfig((AwsKmsCredentialSpecAssumeSTSDTO) credential.getConfig());
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
    return awsKmsCredentialSpecConfig;
  }

  private static AwsKmsCredentialSpecConfig buildManualConfig(AwsKmsCredentialSpecManualConfigDTO configDTO) {
    final String accessKey = configDTO.getAccessKey().getDecryptedValue() == null
        ? null
        : String.valueOf(configDTO.getAccessKey().getDecryptedValue());
    final String secretKey = configDTO.getSecretKey().getDecryptedValue() == null
        ? null
        : String.valueOf(configDTO.getSecretKey().getDecryptedValue());
    return AwsKmsManualCredentialConfig.builder()
        .accessKey(String.valueOf(accessKey))
        .secretKey(String.valueOf(secretKey))
        .build();
  }

  private static AwsKmsCredentialSpecConfig buildIamConfig(AwsKmsCredentialSpecAssumeIAMDTO configDTO) {
    return AwsKmsIamCredentialConfig.builder().delegateSelectors(configDTO.getDelegateSelectors()).build();
  }

  private static AwsKmsCredentialSpecConfig buildStsConfig(AwsKmsCredentialSpecAssumeSTSDTO configDTO) {
    return AwsKmsStsCredentialConfig.builder()
        .delegateSelectors(configDTO.getDelegateSelectors())
        .externalName(configDTO.getExternalName())
        .roleArn(configDTO.getRoleArn())
        .assumeStsRoleDuration(configDTO.getAssumeStsRoleDuration())
        .build();
  }
}
