/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretManagerConfig;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.ImportSecretsDTO;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptionType;

import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SecretsImportService {
  @Inject private SecretManager secretManager;
  @Inject SecretManagerConfigService secretManagerConfigService;
  @Inject DiscoveryService discoveryService;

  private static final Set<EncryptionType> SUPPORTED_SECRET_MANAGERS =
      Sets.newHashSet(EncryptionType.VAULT, EncryptionType.LOCAL);

  public List<NGYamlFile> importConnectors(String authToken, ImportSecretsDTO importSecretsDTO)
      throws IllegalAccessException {
    String accountId = importSecretsDTO.getAccountIdentifier();
    List<String> secretIds;
    switch (importSecretsDTO.getMechanism()) {
      case ALL:
        // Note: All here means all the connectors we support today
        List<String> secretManagerIds =
            secretManager.listSecretManagers(accountId)
                .stream()
                .filter(secretManagerConfig -> {
                  EncryptionType type = secretManagerConfig.getEncryptionType();
                  if (SUPPORTED_SECRET_MANAGERS.contains(type)) {
                    return true;
                  }
                  return EncryptionType.GCP_KMS.equals(type)
                      && "Harness Secrets Manager".equals(secretManagerConfig.getName().trim());
                })
                .map(SecretManagerConfig::getUuid)
                .collect(Collectors.toList());
        List<EncryptedData> encryptedDataList =
            secretManager
                .listSecrets(accountId,
                    PageRequestBuilder.<EncryptedData>aPageRequest()
                        .addFilter(EncryptedDataKeys.kmsId, Operator.IN, secretManagerIds.stream().toArray())
                        .withLimit("UNLIMITED")
                        .build(),
                    null, null, true, false)
                .getResponse();
        secretIds = encryptedDataList.stream().map(EncryptedData::getUuid).collect(Collectors.toList());
        break;
      case SPECIFIC:
        secretIds = importSecretsDTO.getIds();
        break;
      default:
        secretIds = new ArrayList<>();
    }
    DiscoveryResult discoveryResult = discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(
                secretIds.stream()
                    .map(settingId
                        -> DiscoverEntityInput.builder().entityId(settingId).type(NGMigrationEntityType.SECRET).build())
                    .collect(Collectors.toList()))
            .exportImage(false)
            .build());
    MigrationInputDTO migrationInputDTO = MigrationInputDTO.builder()
                                              .accountIdentifier(accountId)
                                              .orgIdentifier(importSecretsDTO.getOrgIdentifier())
                                              .projectIdentifier(importSecretsDTO.getProjectIdentifier())
                                              .migrateReferencedEntities(importSecretsDTO.isMigrateReferencedEntities())
                                              .build();
    return discoveryService.migrateEntity(authToken, migrationInputDTO, discoveryResult, true);
  }
}
