/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryption.EncryptionReflectUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.secrets.setupusage.builders.SettingAttributeSetupUsageBuilder;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SettingAttributeSetupUsageBuilderTest extends WingsBaseTest {
  @Mock SettingsService settingsService;
  @Inject @InjectMocks SettingAttributeSetupUsageBuilder settingAttributeSetupUsageBuilder;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private HPersistence persistence;

  private Account account;
  private EncryptedData encryptedData;
  private SettingAttribute settingAttribute;
  private EncryptionDetail encryptionDetail;

  @Before
  public void setup() {
    initMocks(this);
    account = getAccount(AccountType.PAID);
    account.setUuid(persistence.save(account));

    encryptedData = EncryptedData.builder()
                        .encryptionKey("plainTextKey")
                        .encryptedValue("encryptedValue".toCharArray())
                        .encryptionType(EncryptionType.LOCAL)
                        .type(SettingVariableTypes.GCP_KMS)
                        .kmsId(account.getUuid())
                        .enabled(true)
                        .accountId(account.getUuid())
                        .build();

    encryptedData.setUuid(null);
    encryptedData.setType(SettingVariableTypes.KMS);
    encryptedData.setUuid(UUIDGenerator.generateUuid());

    KubernetesClusterConfig kubernetesClusterConfig =
        KubernetesClusterConfig.builder().accountId(account.getUuid()).build();
    settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                           .withValue(kubernetesClusterConfig)
                           .withAccountId(account.getUuid())
                           .build();
    settingAttribute.setUuid(UUIDGenerator.generateUuid());

    encryptionDetail =
        EncryptionDetail.builder().secretManagerName("secretManagerName").encryptionType(EncryptionType.LOCAL).build();
  }

  private Set<EncryptedDataParent> getEncryptedDataParents() {
    String fieldName1 = EncryptionReflectUtils.getEncryptedFieldTag(
        ReflectionUtils.getFieldByName(settingAttribute.getValue().getClass(), "password"));
    EncryptedDataParent encryptedDataParent1 =
        new EncryptedDataParent(settingAttribute.getUuid(), SettingVariableTypes.KUBERNETES_CLUSTER, fieldName1);

    String fieldName2 = EncryptionReflectUtils.getEncryptedFieldTag(
        ReflectionUtils.getFieldByName(settingAttribute.getValue().getClass(), "caCert"));
    EncryptedDataParent encryptedDataParent2 =
        new EncryptedDataParent(settingAttribute.getUuid(), SettingVariableTypes.KUBERNETES_CLUSTER, fieldName2);

    return Sets.newHashSet(encryptedDataParent1, encryptedDataParent2);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBuildSecretSetupUsage() {
    PageResponse<SettingAttribute> settingAttributePageResponse = mock(PageResponse.class);
    when(settingAttributePageResponse.getResponse()).thenReturn(Collections.singletonList(settingAttribute));
    when(settingsService.list(any(), eq(null), eq(null), eq(false))).thenReturn(settingAttributePageResponse);
    Map<String, Set<EncryptedDataParent>> parentByParentIds = new HashMap<>();
    parentByParentIds.put(settingAttribute.getUuid(), getEncryptedDataParents());

    Set<SecretSetupUsage> returnedSetupUsages = settingAttributeSetupUsageBuilder.buildSecretSetupUsages(
        account.getUuid(), encryptedData.getUuid(), parentByParentIds, encryptionDetail);

    assertThat(returnedSetupUsages).hasSize(2);
    Iterator<SecretSetupUsage> secretSetupUsageIterator = returnedSetupUsages.iterator();
    SecretSetupUsage secretSetupUsage1 = secretSetupUsageIterator.next();
    SecretSetupUsage secretSetupUsage2 = secretSetupUsageIterator.next();

    assertThat(secretSetupUsage1.getEntityId()).isEqualTo(settingAttribute.getUuid());
    assertThat(secretSetupUsage1.getType()).isEqualTo(SettingVariableTypes.KUBERNETES_CLUSTER);
    assertThat(secretSetupUsage1.getEntity()).isEqualTo(settingAttribute);
    assertThat(secretSetupUsage2.getEntityId()).isEqualTo(settingAttribute.getUuid());
    assertThat(secretSetupUsage2.getType()).isEqualTo(SettingVariableTypes.KUBERNETES_CLUSTER);
    assertThat(secretSetupUsage2.getEntity()).isEqualTo(settingAttribute);

    assertThat(Sets.newHashSet(secretSetupUsage1.getFieldName(), secretSetupUsage2.getFieldName()))
        .isEqualTo(Sets.newHashSet("value.password", "value.caCert"));
  }
}
