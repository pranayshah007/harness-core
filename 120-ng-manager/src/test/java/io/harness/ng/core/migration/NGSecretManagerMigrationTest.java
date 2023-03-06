/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.services.ConnectorService;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.dto.GcpKmsConfigDTO;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.security.encryption.EncryptionType;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

public class NGSecretManagerMigrationTest extends NgManagerTestBase {
  private MongoTemplate mongoTemplate;
  private ConnectorService connectorService;
  private SecretManagerConfigDTO cgGlobal;

  private NGSecretManagerService ngSecretManagerService;
  private SecretCrudService secretCrudService;
  private ConnectorMapper connectorMapper;
  private NGEncryptedDataService ngEncryptedDataService;
  private SecretManagerClient secretManagerClient;
  private NGSecretManagerMigration ngSecretManagerMigration;
  private final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  @Before
  public void setup() {
    initMocks(this);
    mongoTemplate = mock(MongoTemplate.class);
    connectorMapper = mock(ConnectorMapper.class);
    connectorService = mock(ConnectorService.class);
    secretCrudService = mock(SecretCrudService.class);
    secretManagerClient = mock(SecretManagerClient.class);
    ngEncryptedDataService = mock(NGEncryptedDataService.class);
    ngSecretManagerService = mock(NGSecretManagerService.class);
    ngSecretManagerMigration = new NGSecretManagerMigration(mongoTemplate, connectorService, ngSecretManagerService,
        secretCrudService, connectorMapper, ngEncryptedDataService, secretManagerClient);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateGlobalGcpKmsSMIfGlobalIsLocalType() {
    SecretManagerConfigDTO globalSM = getLocalConfigDTO();
    when(ngSecretManagerService.getGlobalSecretManagerFromCG(GLOBAL_ACCOUNT_ID)).thenReturn(globalSM);
    ngSecretManagerMigration.createGlobalGcpKmsSM(GLOBAL_ACCOUNT_ID, null, null, true);

    verify(ngSecretManagerService, times(1)).getGlobalSecretManagerFromCG(GLOBAL_ACCOUNT_ID);
    verify(connectorService, times(1)).create(any(), eq(GLOBAL_ACCOUNT_ID), eq(ChangeType.NONE));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateGlobalGcpKmsSMIfGlobalIsNotLocalType() {
    SecretManagerConfigDTO globalSM = getGcpKmsConfigDTO();
    when(ngSecretManagerService.getGlobalSecretManagerFromCG(GLOBAL_ACCOUNT_ID)).thenReturn(globalSM);
    ngSecretManagerMigration.createGlobalGcpKmsSM(GLOBAL_ACCOUNT_ID, null, null, true);
    verify(ngSecretManagerService, times(1)).getGlobalSecretManagerFromCG(GLOBAL_ACCOUNT_ID);
    verify(connectorService, times(2)).create(any(), any(), eq(ChangeType.NONE));
  }

  private SecretManagerConfigDTO getGcpKmsConfigDTO() {
    return GcpKmsConfigDTO.builder()
        .region("region")
        .keyName("keyname")
        .keyRing("keyring")
        .credentials("credentials".toCharArray())
        .projectId("projectId")
        .isDefault(false)
        .encryptionType(EncryptionType.GCP_KMS)
        .name("harness sm")
        .accountIdentifier(GLOBAL_ACCOUNT_ID)
        .orgIdentifier(null)
        .projectIdentifier(null)
        .tags(null)
        .identifier("harnessSecretManager")
        .description(null)
        .harnessManaged(true)
        .build();
  }

  private SecretManagerConfigDTO getLocalConfigDTO() {
    return LocalConfigDTO.builder()
        .isDefault(true)
        .accountIdentifier(GLOBAL_ACCOUNT_ID)
        .encryptionType(EncryptionType.LOCAL)
        .identifier("harnessSecretManager")
        .build();
  }
}
