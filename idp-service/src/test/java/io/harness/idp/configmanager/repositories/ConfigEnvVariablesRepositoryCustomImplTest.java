/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.repositories;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.beans.entity.PluginConfigEnvVariablesEntity;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ConfigEnvVariablesRepositoryCustomImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock MongoTemplate mongoTemplate;

  @InjectMocks ConfigEnvVariablesRepositoryCustomImpl configEnvVariablesRepositoryCustomImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ID = "test_id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  static final String TEST_PLUGIN_ID = "test-plugin-id";
  static final String TEST_PLUGIN_NAME = "test-plugin-name";
  static final String TEST_ENV_NAME = "test-env-name";
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;
  static final long TEST_ENABLED_DISABLED_AT_TIME = 1681756036;

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllEnvVariablesForMultiplePluginIds() {
    PluginConfigEnvVariablesEntity pluginConfigEnvVariablesEntity = getTestPluginConfigVariablesEntity();
    Criteria criteria =
        Criteria.where(PluginConfigEnvVariablesEntity.PluginsConfigEnvVariablesEntityKeys.accountIdentifier)
            .is(pluginConfigEnvVariablesEntity.getAccountIdentifier())
            .and(PluginConfigEnvVariablesEntity.PluginsConfigEnvVariablesEntityKeys.pluginId)
            .in(Arrays.asList(pluginConfigEnvVariablesEntity.getPluginId()));
    Query query = new Query(criteria);
    when(mongoTemplate.find(eq(query), eq(PluginConfigEnvVariablesEntity.class)))
        .thenReturn(Arrays.asList(pluginConfigEnvVariablesEntity));
    assertEquals(Arrays.asList(pluginConfigEnvVariablesEntity),
        configEnvVariablesRepositoryCustomImpl.getAllEnvVariablesForMultiplePluginIds(
            pluginConfigEnvVariablesEntity.getAccountIdentifier(),
            Arrays.asList(pluginConfigEnvVariablesEntity.getPluginId())));
  }

  private PluginConfigEnvVariablesEntity getTestPluginConfigVariablesEntity() {
    return PluginConfigEnvVariablesEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .pluginId(TEST_PLUGIN_ID)
        .pluginName(TEST_PLUGIN_NAME)
        .envName(TEST_ENV_NAME)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME)
        .build();
  }
}
