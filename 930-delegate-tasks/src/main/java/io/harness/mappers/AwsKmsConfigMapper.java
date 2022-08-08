/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsIamCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsManualCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsStsCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.BaseAwsKmsConfigDTO;

import software.wings.beans.KmsConfig;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
@OwnedBy(PL)
public class AwsKmsConfigMapper {
  public static KmsConfig fromDTO(AwsKmsConfigDTO awsKmsConfigDTO) {
    KmsConfig kmsConfig = new KmsConfig();
    kmsConfig.setName(awsKmsConfigDTO.getName());
    populateKmsConfig(kmsConfig, awsKmsConfigDTO.getBaseAwsKmsConfigDTO());

    kmsConfig.setNgMetadata(SecretManagerConfigMapper.ngMetaDataFromDto(awsKmsConfigDTO));
    kmsConfig.setAccountId(awsKmsConfigDTO.getAccountIdentifier());
    kmsConfig.setEncryptionType(awsKmsConfigDTO.getEncryptionType());
    kmsConfig.setDefault(awsKmsConfigDTO.isDefault());
    return kmsConfig;
  }

  public static KmsConfig applyUpdate(KmsConfig kmsConfig, AwsKmsConfigUpdateDTO awsKmsConfigDTO) {
    populateKmsConfig(kmsConfig, awsKmsConfigDTO.getBaseAwsKmsConfigDTO());

    kmsConfig.setDefault(awsKmsConfigDTO.isDefault());
    kmsConfig.setName(awsKmsConfigDTO.getName());
    if (!Optional.ofNullable(kmsConfig.getNgMetadata()).isPresent()) {
      kmsConfig.setNgMetadata(NGSecretManagerMetadata.builder().build());
    }
    kmsConfig.getNgMetadata().setTags(TagMapper.convertToList(awsKmsConfigDTO.getTags()));
    kmsConfig.getNgMetadata().setDescription(awsKmsConfigDTO.getDescription());
    return kmsConfig;
  }

  @NotNull
  private static void populateKmsConfig(KmsConfig kmsConfig, BaseAwsKmsConfigDTO baseAwsKmsConfigDTO) {
    kmsConfig.setKmsArn(baseAwsKmsConfigDTO.getKmsArn());
    kmsConfig.setRegion(baseAwsKmsConfigDTO.getRegion());
    kmsConfig.setAssumeStsRoleOnDelegate(false);
    kmsConfig.setAssumeIamRoleOnDelegate(false);
    kmsConfig.setDelegateSelectors(baseAwsKmsConfigDTO.getDelegateSelectors());

    AwsKmsCredentialType credentialType = baseAwsKmsConfigDTO.getCredentialType();
    switch (credentialType) {
      case MANUAL_CONFIG:
        buildFromManualConfig(kmsConfig, (AwsKmsManualCredentialConfig) baseAwsKmsConfigDTO.getCredential());
        break;
      case ASSUME_IAM_ROLE:
        buildFromIAMConfig(kmsConfig, (AwsKmsIamCredentialConfig) baseAwsKmsConfigDTO.getCredential());
        break;
      case ASSUME_STS_ROLE:
        buildFromSTSConfig(kmsConfig, (AwsKmsStsCredentialConfig) baseAwsKmsConfigDTO.getCredential());
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
  }

  private static void buildFromManualConfig(KmsConfig kmsConfig, AwsKmsManualCredentialConfig credential) {
    kmsConfig.setAccessKey(credential.getAccessKey());
    kmsConfig.setSecretKey(credential.getSecretKey());
  }

  private static void buildFromIAMConfig(KmsConfig kmsConfig, AwsKmsIamCredentialConfig credential) {
    kmsConfig.setAssumeIamRoleOnDelegate(true);
    kmsConfig.setDelegateSelectors(credential.getDelegateSelectors());
  }

  private static void buildFromSTSConfig(KmsConfig kmsConfig, AwsKmsStsCredentialConfig credential) {
    kmsConfig.setAssumeStsRoleOnDelegate(true);
    kmsConfig.setDelegateSelectors(credential.getDelegateSelectors());
    kmsConfig.setExternalName(credential.getExternalName());
    kmsConfig.setRoleArn(credential.getRoleArn());
    kmsConfig.setAssumeStsRoleDuration(credential.getAssumeStsRoleDuration());
  }
}
