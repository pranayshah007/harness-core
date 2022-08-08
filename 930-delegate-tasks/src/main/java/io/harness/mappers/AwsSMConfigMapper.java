/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMConfigDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMManualCredentialConfig;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMStsCredentialConfig;
import io.harness.secretmanagerclient.dto.awssecretmanager.BaseAwsSMConfigDTO;

import software.wings.beans.AwsSecretsManagerConfig;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
@OwnedBy(PL)
public class AwsSMConfigMapper {
  public static AwsSecretsManagerConfig fromDTO(AwsSMConfigDTO awsSMConfigDTO) {
    AwsSecretsManagerConfig awsSecretsManagerConfig = new AwsSecretsManagerConfig();
    awsSecretsManagerConfig.setName(awsSMConfigDTO.getName());
    populateKmsConfig(awsSecretsManagerConfig, awsSMConfigDTO.getBaseAwsSMConfigDTO());

    awsSecretsManagerConfig.setNgMetadata(SecretManagerConfigMapper.ngMetaDataFromDto(awsSMConfigDTO));
    awsSecretsManagerConfig.setAccountId(awsSMConfigDTO.getAccountIdentifier());
    awsSecretsManagerConfig.setEncryptionType(awsSMConfigDTO.getEncryptionType());
    awsSecretsManagerConfig.setDefault(awsSMConfigDTO.isDefault());
    return awsSecretsManagerConfig;
  }

  public static AwsSecretsManagerConfig applyUpdate(
      AwsSecretsManagerConfig awsSecretsManagerConfig, AwsSMConfigUpdateDTO awsSMConfigUpdateDTO) {
    populateKmsConfig(awsSecretsManagerConfig, awsSMConfigUpdateDTO.getBaseAwsSMConfigDTO());

    awsSecretsManagerConfig.setDefault(awsSMConfigUpdateDTO.isDefault());
    awsSecretsManagerConfig.setName(awsSMConfigUpdateDTO.getName());
    if (!Optional.ofNullable(awsSecretsManagerConfig.getNgMetadata()).isPresent()) {
      awsSecretsManagerConfig.setNgMetadata(NGSecretManagerMetadata.builder().build());
    }
    awsSecretsManagerConfig.getNgMetadata().setTags(TagMapper.convertToList(awsSMConfigUpdateDTO.getTags()));
    awsSecretsManagerConfig.getNgMetadata().setDescription(awsSMConfigUpdateDTO.getDescription());
    return awsSecretsManagerConfig;
  }

  @NotNull
  private static void populateKmsConfig(
      AwsSecretsManagerConfig awsSecretsManagerConfig, BaseAwsSMConfigDTO baseAwsSMConfigDTO) {
    awsSecretsManagerConfig.setRegion(baseAwsSMConfigDTO.getRegion());
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(false);
    awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(false);
    awsSecretsManagerConfig.setDelegateSelectors(baseAwsSMConfigDTO.getDelegateSelectors());
    awsSecretsManagerConfig.setSecretNamePrefix(baseAwsSMConfigDTO.getSecretNamePrefix());

    AwsSecretManagerCredentialType credentialType = baseAwsSMConfigDTO.getCredentialType();
    switch (credentialType) {
      case MANUAL_CONFIG:
        buildFromManualConfig(
            awsSecretsManagerConfig, (AwsSMManualCredentialConfig) baseAwsSMConfigDTO.getCredential());
        break;
      case ASSUME_IAM_ROLE:
        awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(true);
        break;
      case ASSUME_STS_ROLE:
        buildFromSTSConfig(awsSecretsManagerConfig, (AwsSMStsCredentialConfig) baseAwsSMConfigDTO.getCredential());
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
  }

  private static void buildFromManualConfig(
      AwsSecretsManagerConfig awsSecretsManagerConfig, AwsSMManualCredentialConfig credential) {
    awsSecretsManagerConfig.setAccessKey(credential.getAccessKey());
    awsSecretsManagerConfig.setSecretKey(credential.getSecretKey());
  }

  private static void buildFromSTSConfig(
      AwsSecretsManagerConfig awsSecretsManagerConfig, AwsSMStsCredentialConfig credential) {
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(true);
    awsSecretsManagerConfig.setExternalName(credential.getExternalId());
    awsSecretsManagerConfig.setRoleArn(credential.getRoleArn());
    awsSecretsManagerConfig.setAssumeStsRoleDuration(credential.getAssumeStsRoleDuration());
  }
}
