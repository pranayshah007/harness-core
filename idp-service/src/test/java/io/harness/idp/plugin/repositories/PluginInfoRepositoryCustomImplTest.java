/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.repositories;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.plugin.beans.PluginInfoEntity;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
public class PluginInfoRepositoryCustomImplTest {
  @InjectMocks private PluginInfoRepositoryCustomImpl pluginInfoRepositoryCustomImpl;

  @Mock private MongoTemplate mongoTemplate;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveOrUpdate() {
    PluginInfoEntity pluginInfoEntity = initializePluginInfoEntity();
    when(mongoTemplate.findOne(any(Query.class), eq(PluginInfoEntity.class))).thenReturn(null);
    when(mongoTemplate.save(any(PluginInfoEntity.class))).thenReturn(pluginInfoEntity);
    PluginInfoEntity entity = pluginInfoRepositoryCustomImpl.saveOrUpdate(pluginInfoEntity);
    assertNotNull(entity);

    when(mongoTemplate.findOne(any(Query.class), eq(PluginInfoEntity.class))).thenReturn(pluginInfoEntity);
    when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(PluginInfoEntity.class)))
        .thenReturn(pluginInfoEntity);
    entity = pluginInfoRepositoryCustomImpl.saveOrUpdate(pluginInfoEntity);
    assertNotNull(entity);
  }

  private PluginInfoEntity initializePluginInfoEntity() {
    return PluginInfoEntity.builder().name("PagerDuty").identifier("pager-duty").build();
  }
}
