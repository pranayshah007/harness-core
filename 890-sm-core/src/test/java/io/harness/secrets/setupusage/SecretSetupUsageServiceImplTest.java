/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.DOCKER;
import static software.wings.settings.SettingVariableTypes.SERVICE_VARIABLE;
import static software.wings.settings.SettingVariableTypes.TRIGGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.SMCoreTestBase;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretsDao;
import io.harness.security.encryption.EncryptionType;

import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;

public class SecretSetupUsageServiceImplTest extends SMCoreTestBase {
  private SecretManagerConfigService secretManagerConfigService;
  private SecretSetupUsageBuilderRegistry secretSetupUsageBuilderRegistry;
  @Inject private SecretsDao secretsDao;
  private SecretSetupUsageServiceImpl secretSetupUsageService;
  private SecretSetupUsageBuilder secretSetupUsageBuilder;
  private EncryptedData encryptedData;
  private String accountId;
  private EncryptionDetail encryptionDetail;

  @Before
  public void setup() {
    secretManagerConfigService = mock(SecretManagerConfigService.class);
    secretSetupUsageBuilderRegistry = mock(SecretSetupUsageBuilderRegistry.class);
    secretSetupUsageService =
        new SecretSetupUsageServiceImpl(secretsDao, secretManagerConfigService, secretSetupUsageBuilderRegistry);
    accountId = UUIDGenerator.generateUuid();

    encryptionDetail = EncryptionDetail.builder()
                           .secretManagerName(UUIDGenerator.generateUuid())
                           .encryptionType(EncryptionType.LOCAL)
                           .build();

    encryptedData = EncryptedData.builder()
                        .encryptionKey("plainTextKey")
                        .encryptedValue("encryptedValue".toCharArray())
                        .encryptionType(EncryptionType.LOCAL)
                        .type(SettingVariableTypes.SECRET_TEXT)
                        .kmsId(accountId)
                        .enabled(true)
                        .accountId(accountId)
                        .name("xyz")
                        .build();

    encryptedData.setUuid(secretsDao.saveSecret(encryptedData));

    secretSetupUsageBuilder = mock(SecretSetupUsageBuilder.class);

    when(secretManagerConfigService.getSecretManagerName(accountId, accountId))
        .thenReturn(encryptionDetail.getSecretManagerName());

    when(secretSetupUsageBuilderRegistry.getSecretSetupUsageBuilder(any()))
        .thenReturn(Optional.of(secretSetupUsageBuilder));
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetSecretUsage_shouldThrowError() {
    secretSetupUsageService.getSecretUsage(accountId, UUIDGenerator.generateUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetSecretUsage_shouldReturnEmpty() {
    Set<SecretSetupUsage> secretSetupUsageSet =
        secretSetupUsageService.getSecretUsage(accountId, encryptedData.getUuid());
    assertThat(secretSetupUsageSet).isEmpty();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetSecretUsage_notSupported() {
    EncryptedDataParent encryptedDataParent1 =
        new EncryptedDataParent(UUIDGenerator.generateUuid(), SERVICE_VARIABLE, null);
    encryptedData.addParent(encryptedDataParent1);
    secretsDao.saveSecret(encryptedData);
    when(secretSetupUsageBuilderRegistry.getSecretSetupUsageBuilder(any())).thenReturn(Optional.empty());

    Set<SecretSetupUsage> secretSetupUsageSet =
        secretSetupUsageService.getSecretUsage(accountId, encryptedData.getUuid());
    assertThat(secretSetupUsageSet).isEmpty();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetSecretUsage_featureFlagEnabled() {
    String commonUuid = UUIDGenerator.generateUuid();
    EncryptedDataParent encryptedDataParent1 =
        new EncryptedDataParent(UUIDGenerator.generateUuid(), SERVICE_VARIABLE, "randomFieldName1");
    EncryptedDataParent encryptedDataParent2 =
        new EncryptedDataParent(UUIDGenerator.generateUuid(), SERVICE_VARIABLE, "randomFieldName1");
    EncryptedDataParent encryptedDataParent3 =
        new EncryptedDataParent(UUIDGenerator.generateUuid(), DOCKER, "randomFieldName2");
    EncryptedDataParent encryptedDataParent4 = new EncryptedDataParent(commonUuid, AWS, "randomFieldName3");
    EncryptedDataParent encryptedDataParent5 = new EncryptedDataParent(commonUuid, AWS, "randomFieldName4");
    EncryptedDataParent encryptedDataParent6 =
        new EncryptedDataParent(UUIDGenerator.generateUuid(), TRIGGER, "randomFieldName5");

    encryptedData.addParent(encryptedDataParent1);
    encryptedData.addParent(encryptedDataParent2);
    encryptedData.addParent(encryptedDataParent3);
    encryptedData.addParent(encryptedDataParent4);
    encryptedData.addParent(encryptedDataParent5);
    encryptedData.addParent(encryptedDataParent6);
    secretsDao.saveSecret(encryptedData);

    Map<String, Set<EncryptedDataParent>> parentIdByParentsMap1 = new HashMap<>();
    parentIdByParentsMap1.put(encryptedDataParent1.getId(), Sets.newHashSet(encryptedDataParent1));
    parentIdByParentsMap1.put(encryptedDataParent2.getId(), Sets.newHashSet(encryptedDataParent2));

    Map<String, Set<EncryptedDataParent>> parentIdByParentsMap2 = new HashMap<>();
    parentIdByParentsMap2.put(encryptedDataParent3.getId(), Sets.newHashSet(encryptedDataParent3));

    Map<String, Set<EncryptedDataParent>> parentIdByParentsMap3 = new HashMap<>();
    parentIdByParentsMap3.put(commonUuid, Sets.newHashSet(encryptedDataParent4, encryptedDataParent5));

    Map<String, Set<EncryptedDataParent>> parentIdByParentsMap4 = new HashMap<>();
    parentIdByParentsMap4.put(encryptedDataParent6.getId(), Sets.newHashSet(encryptedDataParent6));

    SecretSetupUsage mockUsage1 =
        SecretSetupUsage.builder().entityId(encryptedDataParent1.getId()).type(SERVICE_VARIABLE).build();
    SecretSetupUsage mockUsage2 =
        SecretSetupUsage.builder().entityId(encryptedDataParent2.getId()).type(SERVICE_VARIABLE).build();
    SecretSetupUsage mockUsage3 =
        SecretSetupUsage.builder().entityId(encryptedDataParent3.getId()).type(DOCKER).build();
    SecretSetupUsage mockUsage4 = SecretSetupUsage.builder().entityId(commonUuid).type(AWS).build();
    SecretSetupUsage mockUsage5 = SecretSetupUsage.builder().entityId(commonUuid).type(AWS).build();
    SecretSetupUsage mockUsage6 =
        SecretSetupUsage.builder().entityId(encryptedDataParent6.getId()).type(TRIGGER).build();

    when(secretSetupUsageBuilder.buildSecretSetupUsages(
             ArgumentMatchers.eq(accountId), eq(encryptedData.getUuid()), any(), eq(encryptionDetail)))
        .thenReturn(Sets.newHashSet(mockUsage1, mockUsage2))
        .thenReturn(Sets.newHashSet(mockUsage3))
        .thenReturn(Sets.newHashSet(mockUsage4, mockUsage5))
        .thenReturn(Sets.newHashSet(mockUsage6));

    Set<SecretSetupUsage> expectedResponse =
        Sets.newHashSet(mockUsage1, mockUsage2, mockUsage3, mockUsage4, mockUsage5, mockUsage6);

    Set<SecretSetupUsage> secretSetupUsages =
        secretSetupUsageService.getSecretUsage(accountId, encryptedData.getUuid());

    assertThat(secretSetupUsages).isEqualTo(expectedResponse);

    verify(secretManagerConfigService, times(1)).getSecretManagerName(any(), any());
    verify(secretSetupUsageBuilderRegistry, times(1)).getSecretSetupUsageBuilder(SERVICE_VARIABLE);
    verify(secretSetupUsageBuilderRegistry, times(1)).getSecretSetupUsageBuilder(DOCKER);
    verify(secretSetupUsageBuilderRegistry, times(1)).getSecretSetupUsageBuilder(AWS);
    verify(secretSetupUsageBuilderRegistry, times(1)).getSecretSetupUsageBuilder(TRIGGER);
    verify(secretSetupUsageBuilder, times(1))
        .buildSecretSetupUsages(accountId, encryptedData.getUuid(), parentIdByParentsMap1, encryptionDetail);
    verify(secretSetupUsageBuilder, times(1))
        .buildSecretSetupUsages(accountId, encryptedData.getUuid(), parentIdByParentsMap2, encryptionDetail);
    verify(secretSetupUsageBuilder, times(1))
        .buildSecretSetupUsages(accountId, encryptedData.getUuid(), parentIdByParentsMap3, encryptionDetail);
    verify(secretSetupUsageBuilder, times(1))
        .buildSecretSetupUsages(accountId, encryptedData.getUuid(), parentIdByParentsMap4, encryptionDetail);
  }
}
