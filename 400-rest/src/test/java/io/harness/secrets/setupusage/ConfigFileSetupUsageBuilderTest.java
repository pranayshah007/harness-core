/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.secrets.setupusage.builders.ConfigFileSetupUsageBuilder;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.ConfigFileKeys;
import software.wings.beans.EntityType;
import software.wings.beans.ServiceTemplate;
import software.wings.service.intfc.ConfigService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ConfigFileSetupUsageBuilderTest extends WingsBaseTest {
  @Mock private ConfigService configService;
  @Inject @InjectMocks private ConfigFileSetupUsageBuilder configFileSetupUsageBuilder;
  @Inject private HPersistence persistence;

  private List<ConfigFile> configFiles;
  private Account account;
  private EncryptedData encryptedData;
  private EncryptionDetail encryptionDetail;

  @Before
  public void setup() {
    initMocks(this);
    account = getAccount(AccountType.PAID);
    account.setUuid(persistence.save(account));
    configFiles = new ArrayList<>();

    encryptedData = EncryptedData.builder()
                        .encryptionKey("plainTextKey")
                        .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                        .encryptionType(EncryptionType.LOCAL)
                        .type(CONFIG_FILE)
                        .kmsId(UUIDGenerator.generateUuid())
                        .enabled(true)
                        .accountId(account.getUuid())
                        .name("xyz")
                        .fileSize(200)
                        .build();
    String encryptedDataId = persistence.save(encryptedData);
    encryptedData.setUuid(encryptedDataId);

    ConfigFile configFile = ConfigFile.builder()
                                .templateId(UUIDGenerator.generateUuid())
                                .envId(UUIDGenerator.generateUuid())
                                .entityType(EntityType.SERVICE)
                                .entityId(UUIDGenerator.generateUuid())
                                .description(UUIDGenerator.generateUuid())
                                .envIdVersionMapString(UUIDGenerator.generateUuid())
                                .setAsDefault(true)
                                .encryptedFileId(persistence.save(encryptedData))
                                .encrypted(true)
                                .build();

    configFile.setAccountId(account.getUuid());
    configFile.setName(UUIDGenerator.generateUuid());
    configFile.setFileName(UUIDGenerator.generateUuid());
    configFile.setAppId(UUIDGenerator.generateUuid());

    configFiles.add(persistence.get(ConfigFile.class, persistence.save(configFile)));

    ServiceTemplate serviceTemplate = ServiceTemplate.Builder.aServiceTemplate()
                                          .withName(UUIDGenerator.generateUuid())
                                          .withServiceId(UUIDGenerator.generateUuid())
                                          .build();
    String serviceTemplateId = persistence.save(serviceTemplate);

    configFile.setUuid(null);
    configFile.setName(UUIDGenerator.generateUuid());
    configFile.setEntityType(EntityType.SERVICE_TEMPLATE);
    configFile.setEntityId(serviceTemplateId);
    configFiles.add(persistence.get(ConfigFile.class, persistence.save(configFile)));

    encryptionDetail =
        EncryptionDetail.builder().secretManagerName("secretManagerName").encryptionType(EncryptionType.LOCAL).build();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretSetupUsages() {
    PageResponse<ConfigFile> configFilePageResponse = mock(PageResponse.class);
    when(configFilePageResponse.getResponse()).thenReturn(configFiles);
    when(configService.list(any())).thenReturn(configFilePageResponse);

    Map<String, Set<EncryptedDataParent>> parentByParentIds = new HashMap<>();
    parentByParentIds.put(configFiles.get(0).getUuid(), new HashSet<>());
    parentByParentIds.put(configFiles.get(1).getUuid(), new HashSet<>());

    Set<SecretSetupUsage> secretSetupUsages = configFileSetupUsageBuilder.buildSecretSetupUsages(
        account.getUuid(), encryptedData.getUuid(), parentByParentIds, encryptionDetail);

    assertThat(secretSetupUsages).hasSize(2);
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getEntityId).collect(Collectors.toSet()))
        .isEqualTo(parentByParentIds.keySet());
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getType).collect(Collectors.toSet()))
        .isEqualTo(Sets.newHashSet(CONFIG_FILE));
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getFieldName).collect(Collectors.toSet()))
        .isEqualTo(Sets.newHashSet(ConfigFileKeys.encryptedFileId));
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getEntity).collect(Collectors.toSet()))
        .isEqualTo(new HashSet<>(configFiles));
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretSetupUsage_shouldFail() {
    configFiles.get(1).setEntityId(UUIDGenerator.generateUuid());
    PageResponse<ConfigFile> configFilePageResponse = mock(PageResponse.class);
    when(configFilePageResponse.getResponse()).thenReturn(configFiles);
    when(configService.list(any())).thenReturn(configFilePageResponse);

    Map<String, Set<EncryptedDataParent>> parentByParentIds = new HashMap<>();
    parentByParentIds.put(configFiles.get(0).getUuid(), new HashSet<>());
    parentByParentIds.put(configFiles.get(1).getUuid(), new HashSet<>());

    configFileSetupUsageBuilder.buildSecretSetupUsages(
        account.getUuid(), encryptedData.getUuid(), parentByParentIds, encryptionDetail);
  }
}
