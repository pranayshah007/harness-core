/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;
import static io.harness.secretmanagerclient.SecretType.SecretText;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorCredentialDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorCredentialDTO.AwsKmsConnectorCredentialDTOBuilder;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO.AwsKmsConnectorDTOBuilder;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecAssumeIAMDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecAssumeSTSDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecManualConfigDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialType;
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

import software.wings.beans.KmsConfig;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class AwsKmsSecretMigrator implements SecretMigrator {
  @Override
  public SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig secretManagerConfig, String secretManagerIdentifier) {
    return SecretDTOV2.builder()
        .type(SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .valueType(ValueType.Inline)
                  .value(PLEASE_FIX_ME)
                  .secretManagerIdentifier(secretManagerIdentifier)
                  .build());
  }

  @Override
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    KmsConfig kmsConfig = (KmsConfig) secretManagerConfig;
    Set<String> delegateSelectors = kmsConfig.getDelegateSelectors();
    Scope scope = MigratorUtility.getDefaultScope(inputDTO,
        CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(kmsConfig.getUuid()).build(), Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);

    List<SecretDTOV2> secrets = new ArrayList<>();

    String keyArn = String.format("migratedAwsArm_%s",
        MigratorUtility.generateIdentifier(kmsConfig.getName(), inputDTO.getIdentifierCaseFormat()));
    NgEntityDetail keyArnEntityDetail = NgEntityDetail.builder()
                                            .identifier(keyArn)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .build();
    SecretDTOV2 keyArnSecretDTO = getSecretDTO(kmsConfig, inputDTO, keyArn, kmsConfig.getKmsArn());
    secrets.add(keyArnSecretDTO);

    AwsKmsConnectorDTOBuilder connectorDTO =
        AwsKmsConnectorDTO.builder()
            .delegateSelectors(delegateSelectors)
            .kmsArn(
                SecretRefData.builder().scope(MigratorUtility.getScope(keyArnEntityDetail)).identifier(keyArn).build())
            .region(kmsConfig.getRegion());

    AwsKmsConnectorCredentialDTOBuilder credentialDTOBuilder = AwsKmsConnectorCredentialDTO.builder();

    // Handle Auth Token
    if (StringUtils.isNotBlank(kmsConfig.getAccessKey())) {
      String awsAccessKey = String.format("migratedAwsKey_%s",
          MigratorUtility.generateIdentifier(kmsConfig.getName(), inputDTO.getIdentifierCaseFormat()));
      NgEntityDetail awsAccessKeyEntityDetail = NgEntityDetail.builder()
                                                    .identifier(awsAccessKey)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build();
      String awsSecretKey = String.format("migratedAwsSecret_%s",
          MigratorUtility.generateIdentifier(kmsConfig.getName(), inputDTO.getIdentifierCaseFormat()));
      NgEntityDetail awsSecretEntityDetail = NgEntityDetail.builder()
                                                 .identifier(awsAccessKey)
                                                 .orgIdentifier(orgIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .build();
      SecretDTOV2 awsSecretDTO = getSecretDTO(kmsConfig, inputDTO, awsSecretKey, kmsConfig.getSecretKey());
      SecretDTOV2 awsAccessDTO = getSecretDTO(kmsConfig, inputDTO, awsAccessKey, kmsConfig.getAccessKey());
      credentialDTOBuilder.credentialType(AwsKmsCredentialType.MANUAL_CONFIG)
          .config(AwsKmsCredentialSpecManualConfigDTO.builder()
                      .accessKey(SecretRefData.builder()
                                     .scope(MigratorUtility.getScope(awsAccessKeyEntityDetail))
                                     .identifier(awsAccessKey)
                                     .build())
                      .secretKey(SecretRefData.builder()
                                     .scope(MigratorUtility.getScope(awsSecretEntityDetail))
                                     .identifier(awsSecretKey)
                                     .build())
                      .build());
      secrets.add(awsSecretDTO);
      secrets.add(awsAccessDTO);
    } else if (StringUtils.isNotBlank(kmsConfig.getRoleArn())) {
      credentialDTOBuilder.credentialType(AwsKmsCredentialType.ASSUME_STS_ROLE)
          .config(AwsKmsCredentialSpecAssumeSTSDTO.builder()
                      .assumeStsRoleDuration(kmsConfig.getAssumeStsRoleDuration())
                      .externalName(kmsConfig.getExternalName())
                      .roleArn(kmsConfig.getRoleArn())
                      .delegateSelectors(delegateSelectors)
                      .build());
    } else {
      credentialDTOBuilder.credentialType(AwsKmsCredentialType.ASSUME_IAM_ROLE)
          .config(AwsKmsCredentialSpecAssumeIAMDTO.builder().delegateSelectors(delegateSelectors).build());
    }

    return SecretManagerCreatedDTO.builder()
        .connector(connectorDTO.credential(credentialDTOBuilder.build()).build())
        .secrets(secrets.stream()
                     .map(secretDTOV2 -> CustomSecretRequestWrapper.builder().secret(secretDTOV2).build())
                     .collect(Collectors.toList()))
        .build();
  }
}
