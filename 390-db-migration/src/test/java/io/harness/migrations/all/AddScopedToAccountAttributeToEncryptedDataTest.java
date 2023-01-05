/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.dl.WingsPersistence;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AddScopedToAccountAttributeToEncryptedDataTest extends WingsBaseTest {
  @Inject WingsPersistence wingsPersistence;
  @Inject AddScopedToAccountAttributeToEncryptedData addScopedToAccountAttributeToEncryptedData;

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testMigrate() {
    Set<AppEnvRestriction> appEnvRestrictions = new HashSet();
    appEnvRestrictions.add(AppEnvRestriction.builder()
                               .appFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
                               .build());
    UsageRestrictions usageRestrictions = UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();

    EncryptedData encryptedData =
        createEncryptedRecord(UUID.randomUUID().toString(), usageRestrictions, SettingVariableTypes.SECRET_TEXT);
    String notUpdateSecretId = wingsPersistence.save(encryptedData);

    Set<String> envFilterTypes = new HashSet<>();
    envFilterTypes.add(EnvFilter.FilterType.PROD);

    Set<String> envFilterIds = new HashSet<>();
    envFilterIds.add(EnvFilter.FilterType.PROD);

    Set<AppEnvRestriction> configAppEnvRestrictions = new HashSet();
    configAppEnvRestrictions.add(
        AppEnvRestriction.builder()
            .envFilter(EnvFilter.builder().ids(envFilterIds).filterTypes(envFilterTypes).build())
            .build());
    UsageRestrictions configUsageRestrictions =
        UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();

    EncryptedData configEncryptedData =
        createEncryptedRecord(UUID.randomUUID().toString(), configUsageRestrictions, SettingVariableTypes.CONFIG_FILE);
    String notUpdateConfigId = wingsPersistence.save(configEncryptedData);

    EncryptedData encryptedSecretWithNoRestrictions =
        createEncryptedRecord(UUID.randomUUID().toString(), null, SettingVariableTypes.SECRET_TEXT);
    String updatedSecretTextId = wingsPersistence.save(encryptedSecretWithNoRestrictions);

    EncryptedData encryptedConfigWithNoRestrictions =
        createEncryptedRecord(UUID.randomUUID().toString(), null, SettingVariableTypes.CONFIG_FILE);
    String updatedConfigFileId = wingsPersistence.save(encryptedConfigWithNoRestrictions);

    EncryptedData encryptedDataWithoutAccountId =
        createEncryptedRecord(null, usageRestrictions, SettingVariableTypes.SECRET_TEXT);
    String nonAccountnSecretId = wingsPersistence.save(encryptedDataWithoutAccountId);

    addScopedToAccountAttributeToEncryptedData.migrate();

    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                                     .field(EncryptedDataKeys.scopedToAccount)
                                     .equal(true);
    List<EncryptedData> updateEncryptedDataList = query.asList();

    assertThat(updateEncryptedDataList.size()).isEqualTo(2);
    assertThat(updatedSecretTextId.equals(updateEncryptedDataList.get(0).getUuid())
        || updatedConfigFileId.equals(updateEncryptedDataList.get(0).getUuid()))
        .isTrue();
    assertThat(updatedSecretTextId.equals(updateEncryptedDataList.get(1).getUuid())
        || updatedConfigFileId.equals(updateEncryptedDataList.get(1).getUuid()))
        .isTrue();

    query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                .field(EncryptedDataKeys.accountId)
                .exists()
                .field(EncryptedDataKeys.scopedToAccount)
                .equal(false);

    List<EncryptedData> notUpdateEncryptedDataList = query.asList();

    assertThat(updateEncryptedDataList.size()).isEqualTo(2);
    assertThat(notUpdateSecretId.equals(notUpdateEncryptedDataList.get(0).getUuid())
        || notUpdateConfigId.equals(notUpdateEncryptedDataList.get(0).getUuid()))
        .isTrue();
    assertThat(notUpdateSecretId.equals(notUpdateEncryptedDataList.get(1).getUuid())
        || notUpdateConfigId.equals(notUpdateEncryptedDataList.get(1).getUuid()))
        .isTrue();

    query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority)
                .field(EncryptedDataKeys.accountId)
                .doesNotExist()
                .field(EncryptedDataKeys.scopedToAccount)
                .equal(false);

    notUpdateEncryptedDataList = query.asList();

    assertThat(notUpdateEncryptedDataList.size()).isEqualTo(1);
    assertThat(notUpdateEncryptedDataList.get(0).getUuid()).isEqualTo(nonAccountnSecretId);
  }

  private EncryptedData createEncryptedRecord(
      String accountId, UsageRestrictions usageRestrictions, SettingVariableTypes type) {
    return EncryptedData.builder()
        .accountId(accountId)
        .kmsId(UUID.randomUUID().toString())
        .type(type)
        .usageRestrictions(usageRestrictions)
        .build();
  }
}
