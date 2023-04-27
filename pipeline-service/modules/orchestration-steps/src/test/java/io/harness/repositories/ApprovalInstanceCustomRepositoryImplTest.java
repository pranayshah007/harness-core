/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;

import com.mongodb.client.result.DeleteResult;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class ApprovalInstanceCustomRepositoryImplTest extends OrchestrationStepsTestBase {
  @Mock MongoTemplate mongoTemplate;
  @Mock DeleteResult deleteResult;
  ApprovalInstanceCustomRepository approvalInstanceCustomRepository;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateFirst() {
    approvalInstanceCustomRepository = new ApprovalInstanceCustomRepositoryImpl(mongoTemplate);
    Query query = new Query(Criteria.where(ApprovalInstanceKeys.nodeExecutionId).is("hello"));
    Update update = new Update();
    doReturn(null).when(mongoTemplate).findAndModify(any(), any(), any(), anyString());
    approvalInstanceCustomRepository.updateFirst(query, update);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    ArgumentCaptor<FindAndModifyOptions> findAndModifyOptionsArgumentCaptor =
        ArgumentCaptor.forClass(FindAndModifyOptions.class);
    ArgumentCaptor<Class> stringArgumentCaptor = ArgumentCaptor.forClass(Class.class);
    verify(mongoTemplate, times(1))
        .findAndModify(queryArgumentCaptor.capture(), updateArgumentCaptor.capture(),
            findAndModifyOptionsArgumentCaptor.capture(), stringArgumentCaptor.capture());
    assertEquals(queryArgumentCaptor.getValue(), query);
    assertEquals(updateArgumentCaptor.getValue(), update);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateMulti() {
    approvalInstanceCustomRepository = new ApprovalInstanceCustomRepositoryImpl(mongoTemplate);
    Query query = new Query(Criteria.where(ApprovalInstanceKeys.nodeExecutionId).is("hello"));
    Update update = new Update();
    doReturn(null).when(mongoTemplate).updateMulti(any(), any(), anyString());
    approvalInstanceCustomRepository.updateMulti(query, update);
    verify(mongoTemplate, times(1)).updateMulti(query, update, ApprovalInstance.class);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testFindAll() {
    approvalInstanceCustomRepository = new ApprovalInstanceCustomRepositoryImpl(mongoTemplate);
    Criteria criteria = new Criteria();
    doReturn(null).when(mongoTemplate).find(any(), any());
    Query query = new Query(criteria);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    approvalInstanceCustomRepository.findAll(criteria);
    verify(mongoTemplate, times(1)).find(queryArgumentCaptor.capture(), any());
    assertEquals(queryArgumentCaptor.getValue(), query);
  }
}
