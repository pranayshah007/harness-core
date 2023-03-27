/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import static io.harness.secretmanagerclient.SecretType.SecretText;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecAssumeSTSDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecManualConfigDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialType;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerDTO.AwsSecretManagerDTOBuilder;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.CustomSecretRequestWrapper;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.secretmanagerclient.ValueType;

import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class AwsSecretMigrator implements SecretMigrator {
  @Override
  public SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig secretManagerConfig, String secretManagerIdentifier) {
    String value;
    if (StringUtils.isNotBlank(encryptedData.getPath())) {
      value = encryptedData.getPath();
    } else {
      value = encryptedData.getEncryptionKey();
    }
    return SecretDTOV2.builder()
        .type(SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .valueType(ValueType.Reference)
                  .value(value)
                  .secretManagerIdentifier(secretManagerIdentifier)
                  .build());
  }

  @Override
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    AwsSecretsManagerConfig awsSecretsManagerConfig = (AwsSecretsManagerConfig) secretManagerConfig;

    Scope scope = MigratorUtility.getDefaultScope(inputDTO,
        CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(awsSecretsManagerConfig.getUuid()).build(),
        Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);

    AwsSecretManagerDTOBuilder connectorDTO = AwsSecretManagerDTO.builder()
                                                  .secretNamePrefix(awsSecretsManagerConfig.getSecretNamePrefix())
                                                  .region(awsSecretsManagerConfig.getRegion())
                                                  .delegateSelectors(awsSecretsManagerConfig.getDelegateSelectors());

    List<SecretDTOV2> secrets = new ArrayList<>();

    // Handle Auth Token
    if (StringUtils.isNotBlank(awsSecretsManagerConfig.getAccessKey())) {
      String awsAccessKey = String.format("migratedAwsKey_%s",
          MigratorUtility.generateIdentifier(awsSecretsManagerConfig.getName(), inputDTO.getIdentifierCaseFormat()));
      NgEntityDetail awsAccessKeyEntityDetail = NgEntityDetail.builder()
                                                    .entityType(NGMigrationEntityType.SECRET)
                                                    .identifier(awsAccessKey)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();
      String awsSecretKey = String.format("migratedAwsSecret_%s",
          MigratorUtility.generateIdentifier(awsSecretsManagerConfig.getName(), inputDTO.getIdentifierCaseFormat()));
      NgEntityDetail awsSecretEntityDetail = NgEntityDetail.builder()
                                                 .entityType(NGMigrationEntityType.SECRET)
                                                 .identifier(awsAccessKey)
                                                 .orgIdentifier(orgIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .build();
      SecretDTOV2 awsSecretDTO =
          getSecretDTO(awsSecretsManagerConfig, inputDTO, awsSecretKey, awsSecretsManagerConfig.getSecretKey());
      SecretDTOV2 awsAccessDTO =
          getSecretDTO(awsSecretsManagerConfig, inputDTO, awsAccessKey, awsSecretsManagerConfig.getAccessKey());
      connectorDTO.credential(AwsSecretManagerCredentialDTO.builder()
                                  .credentialType(AwsSecretManagerCredentialType.MANUAL_CONFIG)
                                  .config(AwsSMCredentialSpecManualConfigDTO.builder()
                                              .accessKey(SecretRefData.builder()
                                                             .scope(MigratorUtility.getScope(awsAccessKeyEntityDetail))
                                                             .identifier(awsAccessKey)
                                                             .build())
                                              .secretKey(SecretRefData.builder()
                                                             .scope(MigratorUtility.getScope(awsSecretEntityDetail))
                                                             .identifier(awsSecretKey)
                                                             .build())
                                              .build())
                                  .build());

      secrets.add(awsSecretDTO);
      secrets.add(awsAccessDTO);
    } else if (StringUtils.isNotBlank(awsSecretsManagerConfig.getRoleArn())) {
      connectorDTO.credential(AwsSecretManagerCredentialDTO.builder()
                                  .credentialType(AwsSecretManagerCredentialType.ASSUME_STS_ROLE)
                                  .config(AwsSMCredentialSpecAssumeSTSDTO.builder()
                                              .externalId(awsSecretsManagerConfig.getExternalName())
                                              .roleArn(awsSecretsManagerConfig.getRoleArn())
                                              .assumeStsRoleDuration(awsSecretsManagerConfig.getAssumeStsRoleDuration())
                                              .build())
                                  .build());
    } else {
      connectorDTO.credential(AwsSecretManagerCredentialDTO.builder()
                                  .credentialType(AwsSecretManagerCredentialType.ASSUME_IAM_ROLE)
                                  .build());
    }

    return SecretManagerCreatedDTO.builder()
        .connector(connectorDTO.build())
        .secrets(secrets.stream()
                     .map(secretDTOV2 -> CustomSecretRequestWrapper.builder().secret(secretDTOV2).build())
                     .collect(Collectors.toList()))
        .build();
  }
}
