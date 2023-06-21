/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.repositories;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.rule.Owner;

import java.util.Arrays;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class AppConfigRepositoryCustomImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock MongoTemplate mongoTemplate;

  @InjectMocks AppConfigRepositoryCustomImpl appConfigRepositoryCustomImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ID = "test_id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  static final ConfigType TEST_PLUGIN_CONFIG_TYPE = ConfigType.PLUGIN;
  static final String TEST_CONFIG_ID = "test-config-id";
  static final String TEST_CONFIG_NAME = "test-config-name";
  static final String TEST_CONFIG_VALUE = "test-plugin-config";
  static final Boolean TEST_ENABLED = true;
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;
  static final long TEST_ENABLED_DISABLED_AT_TIME = 1681756036;

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateAppConfig() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    Criteria criteria = appConfigRepositoryCustomImpl.getCriteriaForConfig(
        appConfigEntity.getAccountIdentifier(), appConfigEntity.getConfigId(), appConfigEntity.getConfigType());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(AppConfigEntity.AppConfigEntityKeys.configs, appConfigEntity.getConfigs());
    update.set(AppConfigEntity.AppConfigEntityKeys.lastModifiedAt, System.currentTimeMillis());
    when(mongoTemplate.findAndModify(
             eq(query), any(Update.class), any(FindAndModifyOptions.class), eq(AppConfigEntity.class)))
        .thenReturn(appConfigEntity);

    assertEquals(appConfigEntity, appConfigRepositoryCustomImpl.updateConfig(appConfigEntity, TEST_PLUGIN_CONFIG_TYPE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateConfigEnablement() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    Criteria criteria = appConfigRepositoryCustomImpl.getCriteriaForConfig(
        appConfigEntity.getAccountIdentifier(), appConfigEntity.getConfigId(), appConfigEntity.getConfigType());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(AppConfigEntity.AppConfigEntityKeys.enabled, TEST_ENABLED);
    update.set(AppConfigEntity.AppConfigEntityKeys.lastModifiedAt, System.currentTimeMillis());
    when(mongoTemplate.findAndModify(
             eq(query), any(Update.class), any(FindAndModifyOptions.class), eq(AppConfigEntity.class)))
        .thenReturn(appConfigEntity);

    assertEquals(appConfigEntity,
        appConfigRepositoryCustomImpl.updateConfigEnablement(appConfigEntity.getAccountIdentifier(),
            appConfigEntity.getConfigId(), appConfigEntity.getEnabled(), appConfigEntity.getConfigType()));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    Criteria criteria = Criteria.where(AppConfigEntity.AppConfigEntityKeys.enabledDisabledAt)
                            .lte(TEST_ENABLED_DISABLED_AT_TIME)
                            .and(AppConfigEntity.AppConfigEntityKeys.configType)
                            .is(ConfigType.PLUGIN)
                            .and(AppConfigEntity.AppConfigEntityKeys.enabled)
                            .is(false);
    Query query = new Query(criteria);
    when(mongoTemplate.findAllAndRemove(eq(query), eq(AppConfigEntity.class)))
        .thenReturn(Arrays.asList(appConfigEntity));

    assertEquals(Arrays.asList(appConfigEntity),
        appConfigRepositoryCustomImpl.deleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime(
            TEST_ENABLED_DISABLED_AT_TIME));
  }

  private AppConfigEntity getTestAppConfigEntity() {
    return AppConfigEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .configType(TEST_PLUGIN_CONFIG_TYPE)
        .configId(TEST_CONFIG_ID)
        .configName(TEST_CONFIG_NAME)
        .configs(TEST_CONFIG_VALUE)
        .enabled(TEST_ENABLED)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME)
        .build();
  }
}
