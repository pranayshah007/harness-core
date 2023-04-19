/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instancesyncperpetualtaskinfo;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class InstanceSyncPerpetualTaskInfoRepositoryCustomImplTest extends InstancesTestBase {
  @Mock InstanceSyncPerpetualTaskInfo instanceSyncPerpetualTaskInfo;
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks InstanceSyncPerpetualTaskInfoRepositoryCustomImpl instanceSyncPerpetualTaskInfoRepositoryCustom;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void updateTest() {
    Criteria criteria = Criteria.where("key");
    Query query = new Query(criteria);
    Update update = new Update();
    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(InstanceSyncPerpetualTaskInfo.class)))
        .thenReturn(instanceSyncPerpetualTaskInfo);
    assertThat(instanceSyncPerpetualTaskInfoRepositoryCustom.update(criteria, update))
        .isEqualTo(instanceSyncPerpetualTaskInfo);
  }
}
