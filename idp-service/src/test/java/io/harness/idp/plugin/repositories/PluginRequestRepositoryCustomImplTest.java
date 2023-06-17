/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.repositories;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.plugin.beans.PluginRequestEntity;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.IDP)
public class PluginRequestRepositoryCustomImplTest {
  @InjectMocks private PluginRequestRepositoryCustomImpl pluginRequestRepositoryCustom;

  @Mock private MongoTemplate mongoTemplate;

  private static final String ACCOUNT_ID = "123";

  private static final String PLUGIN_REQUEST_NAME = "pluginName";
  private static final String PLUGIN_REQUEST_CREATOR = "foo";
  private static final String PLUGIN_REQUEST_PACKAGE_LINK = "https://www.harness.io";
  private static final String PLUGIN_REQUEST_DOC_LINK = "https://www.harness.io";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFindAll() {
    Criteria criteria = Criteria.where(PluginRequestEntity.PluginRequestKeys.accountIdentifier).is(ACCOUNT_ID);
    Query query = new Query(criteria).with(PageRequest.of(0, 10));
    List<PluginRequestEntity> pluginRequestEntities = getPagePluginRequestEntity();
    when(mongoTemplate.find(query, PluginRequestEntity.class)).thenReturn(pluginRequestEntities);
    Page<PluginRequestEntity> entity = pluginRequestRepositoryCustom.findAll(criteria, PageRequest.of(0, 10));
    assertNotNull(entity);
    assertEquals(entity.getContent().get(0).getName(), PLUGIN_REQUEST_NAME);
    assertEquals(entity.getContent().get(0).getCreator(), PLUGIN_REQUEST_CREATOR);
    assertEquals(entity.getContent().get(0).getPackageLink(), PLUGIN_REQUEST_PACKAGE_LINK);
    assertEquals(entity.getContent().get(0).getDocLink(), PLUGIN_REQUEST_DOC_LINK);
  }

  private List<PluginRequestEntity> getPagePluginRequestEntity() {
    PluginRequestEntity pluginRequestEntity = PluginRequestEntity.builder()
                                                  .name(PLUGIN_REQUEST_NAME)
                                                  .creator(PLUGIN_REQUEST_CREATOR)
                                                  .packageLink(PLUGIN_REQUEST_PACKAGE_LINK)
                                                  .docLink(PLUGIN_REQUEST_DOC_LINK)
                                                  .build();
    List<PluginRequestEntity> pluginRequestEntityList = new ArrayList<>();
    pluginRequestEntityList.add(pluginRequestEntity);
    return pluginRequestEntityList;
  }
}
