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
import io.harness.idp.configmanager.beans.entity.PluginsProxyInfoEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.Collections;
import java.util.List;
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
public class PluginsProxyInfoRepositoryCustomImplTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock MongoTemplate mongoTemplate;

  @InjectMocks PluginsProxyInfoRepositoryCustomImpl pluginsProxyInfoRepositoryCustomImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ID = "test_id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test_account_identifier";
  static final String TEST_PLUGIN_ID = "test_plugin_id";
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;
  static final String TEST_HOST_VALUE = "test_host_value";
  static final Boolean TEST_PROXY_VALUE = true;
  static final String TEST_DELEGATE_SELECTOR_NAME = "test_delegate_selector";
  static final List<String> TEST_DELEGATE_SELECTOR_LIST = Collections.singletonList(TEST_DELEGATE_SELECTOR_NAME);
  static final Boolean TEST_UPDATE_PROXY_VALUE = false;

  static final String TEST_UPDATE_DELEGATE_SELECTOR = "test_update_delegate_selector";
  static final List<String> TEST_UPDATE_DELEGATE_SELECTOR_LIST =
      Collections.singletonList(TEST_UPDATE_DELEGATE_SELECTOR);

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFindAllByAccountIdentifierAndPluginIds() {
    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    Criteria criteria = Criteria.where(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.accountIdentifier)
                            .is(pluginsProxyInfoEntity.getAccountIdentifier())
                            .and(PluginsProxyInfoEntity.PluginProxyInfoEntityKeys.pluginId)
                            .in(Collections.singletonList(pluginsProxyInfoEntity.getPluginId()));
    Query query = new Query(criteria);
    when(mongoTemplate.find(eq(query), eq(PluginsProxyInfoEntity.class)))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    assertEquals(Collections.singletonList(pluginsProxyInfoEntity),
        pluginsProxyInfoRepositoryCustomImpl.findAllByAccountIdentifierAndPluginIds(
            pluginsProxyInfoEntity.getAccountIdentifier(),
            Collections.singletonList(pluginsProxyInfoEntity.getPluginId())));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdatePluginProxyInfo() {
    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    pluginsProxyInfoEntity.setProxy(TEST_UPDATE_PROXY_VALUE);
    pluginsProxyInfoEntity.setDelegateSelectors(TEST_UPDATE_DELEGATE_SELECTOR_LIST);
    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PluginsProxyInfoEntity.class)))
        .thenReturn(pluginsProxyInfoEntity);
    ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
    proxyHostDetail.setProxy(TEST_UPDATE_PROXY_VALUE);
    proxyHostDetail.setSelectors(TEST_UPDATE_DELEGATE_SELECTOR_LIST);
    proxyHostDetail.setHost(TEST_HOST_VALUE);
    pluginsProxyInfoEntity =
        pluginsProxyInfoRepositoryCustomImpl.updatePluginProxyInfo(proxyHostDetail, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_UPDATE_PROXY_VALUE, pluginsProxyInfoEntity.getProxy());
    assertEquals(TEST_UPDATE_DELEGATE_SELECTOR, pluginsProxyInfoEntity.getDelegateSelectors().get(0));
  }

  private PluginsProxyInfoEntity getTestPluginProxyInfoEntity() {
    return PluginsProxyInfoEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .pluginId(TEST_PLUGIN_ID)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .host(TEST_HOST_VALUE)
        .proxy(TEST_PROXY_VALUE)
        .delegateSelectors(TEST_DELEGATE_SELECTOR_LIST)
        .build();
  }
}
