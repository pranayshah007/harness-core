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
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO.GcpSecretManagerConnectorDTOBuilder;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.CustomSecretRequestWrapper;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.security.encryption.AdditionalMetadata;

import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class GcpSecretMigrator implements SecretMigrator {
  @Override
  public SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig secretManagerConfig, String secretManagerIdentifier) {
    String value;
    String version;
    // If encryption key exists then it is inline secret in CG.
    if (StringUtils.isNotBlank(encryptedData.getEncryptionKey())) {
      value = encryptedData.getEncryptionKey();
      version = "latest";
    } else {
      value = encryptedData.getName();
      version = encryptedData.getPath();
    }
    return SecretDTOV2.builder()
        .type(SecretText)
        .spec(
            SecretTextSpecDTO.builder()
                .valueType(ValueType.Reference)
                .value(value)
                .secretManagerIdentifier(secretManagerIdentifier)
                .additionalMetadata(AdditionalMetadata.builder()
                                        .values(ImmutableMap.<String, Object>builder().put("version", version).build())
                                        .build())
                .build());
  }

  @Override
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = (GcpSecretsManagerConfig) secretManagerConfig;

    Scope scope = MigratorUtility.getDefaultScope(inputDTO,
        CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(gcpSecretsManagerConfig.getUuid()).build(),
        Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);

    String gcpSecretFileIdentifier = String.format("migratedGcpSM_%s",
        MigratorUtility.generateIdentifier(gcpSecretsManagerConfig.getName(), inputDTO.getIdentifierCaseFormat()));

    NgEntityDetail gcpEntityDetail = NgEntityDetail.builder()
                                         .entityType(NGMigrationEntityType.SECRET)
                                         .identifier(gcpSecretFileIdentifier)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .build();

    SecretRefData secretRefData = SecretRefData.builder()
                                      .scope(MigratorUtility.getScope(gcpEntityDetail))
                                      .identifier(gcpSecretFileIdentifier)
                                      .build();

    GcpSecretManagerConnectorDTOBuilder connectorDTO =
        GcpSecretManagerConnectorDTO.builder()
            .credentialsRef(secretRefData)
            .delegateSelectors(gcpSecretsManagerConfig.getDelegateSelectors());

    List<CustomSecretRequestWrapper> secrets = Collections.singletonList(
        CustomSecretRequestWrapper.builder()
            .fileContent(String.valueOf(gcpSecretsManagerConfig.getCredentials()))
            .secret(SecretDTOV2.builder()
                        .identifier(gcpSecretFileIdentifier)
                        .name(gcpSecretFileIdentifier)
                        .description(String.format(
                            "Auto Generated Secret for Secret Manager - %s", gcpSecretsManagerConfig.getName()))
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .type(SecretType.SecretFile)
                        .spec(SecretFileSpecDTO.builder().secretManagerIdentifier("harnessSecretManager").build())
                        .build())
            .build());

    return SecretManagerCreatedDTO.builder().connector(connectorDTO.build()).secrets(secrets).build();
  }
}
