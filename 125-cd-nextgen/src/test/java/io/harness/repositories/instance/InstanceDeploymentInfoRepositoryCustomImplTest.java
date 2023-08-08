/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfo.InstanceDeploymentInfoKeys;
import io.harness.rule.Owner;

import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.TransactionTimedOutException;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class InstanceDeploymentInfoRepositoryCustomImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org";
  private static final String PROJECT_ID = "test-project";

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private InstanceDeploymentInfoRepositoryCustomImpl repository;

  @Before
  public void setup() {
    Reflect.on(repository).set("mongoTemplate", mongoTemplate);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testDeleteByScope() {
    Criteria criteria = new Criteria();
    criteria.and(InstanceDeploymentInfoKeys.accountIdentifier)
        .is(ACCOUNT_ID)
        .and(InstanceDeploymentInfoKeys.orgIdentifier)
        .is(ORG_ID)
        .and(InstanceDeploymentInfoKeys.projectIdentifier)
        .is(PROJECT_ID);
    Query query = new Query(criteria);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    repository.deleteByScope(scope);

    verify(mongoTemplate).remove(queryArgumentCaptor.capture(), eq(InstanceDeploymentInfo.class));
    assertThat(queryArgumentCaptor.getValue().getQueryObject()).isEqualTo(query.getQueryObject());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testDeleteByScopeRetries() {
    Scope scope = Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    when(mongoTemplate.remove(any(), eq(InstanceDeploymentInfo.class)))
        .thenThrow(new TransactionTimedOutException("Failed"));

    repository.deleteByScope(scope);
    verify(mongoTemplate, times(3)).remove(any(), eq(InstanceDeploymentInfo.class));
  }
}
