/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.rule.Owner;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.DeleteResult;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class TriggerEventHistoryRepositoryCustomImplTest extends CategoryTest {
  @InjectMocks TriggerEventHistoryRepositoryCustomImpl triggerEventHistoryRepositoryCustom;
  @Mock MongoTemplate mongoTemplate;
  @Mock BulkOperations bulkOperations;
  @Mock BulkWriteResult bulkWriteResult;
  @Mock TriggerEventHistoryReadHelper triggerEventHistoryReadHelper;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindAll() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder().build();
    Criteria criteria = new Criteria();
    when(mongoTemplate.find(any(), any())).thenReturn(Collections.singletonList(triggerEventHistory));
    List<TriggerEventHistory> triggerEventHistories = Collections.singletonList(triggerEventHistory);
    assertThat(triggerEventHistoryRepositoryCustom.findAll(criteria)).isEqualTo(triggerEventHistories);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPaginatedFindAll() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder().build();
    Criteria criteria = new Criteria();
    Pageable pageable = PageRequest.of(1, 1);

    when(triggerEventHistoryReadHelper.find(any())).thenReturn(Collections.singletonList(triggerEventHistory));
    when(triggerEventHistoryReadHelper.findCount(any())).thenReturn(1L);
    List<TriggerEventHistory> triggerEventHistories = Collections.singletonList(triggerEventHistory);
    assertThat(triggerEventHistoryRepositoryCustom.findAll(criteria, pageable).getContent())
        .isEqualTo(triggerEventHistories);

    // Exception case
    when(triggerEventHistoryReadHelper.find(any())).thenThrow(new IllegalArgumentException());
    assertThatThrownBy(() -> triggerEventHistoryRepositoryCustom.findAll(criteria, pageable))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Trigger event history not found");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindAllActivationTimestampsInRange() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder().build();
    Criteria criteria = new Criteria();
    when(mongoTemplate.find(any(), any())).thenReturn(Collections.singletonList(triggerEventHistory));
    List<TriggerEventHistory> triggerEventHistories = Collections.singletonList(triggerEventHistory);
    assertThat(triggerEventHistoryRepositoryCustom.findAllActivationTimestampsInRange(criteria))
        .isEqualTo(triggerEventHistories);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteBatch() {
    Criteria criteria = new Criteria();
    when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TriggerEventHistory.class))
        .thenReturn(bulkOperations);
    when(bulkOperations.remove((Query) any())).thenReturn(bulkOperations);
    when(bulkOperations.execute()).thenReturn(bulkWriteResult);
    triggerEventHistoryRepositoryCustom.deleteBatch(criteria);
    verify(mongoTemplate).bulkOps(BulkOperations.BulkMode.UNORDERED, TriggerEventHistory.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteTriggerEventHistoryForTriggerIdentifier() {
    Criteria criteria = new Criteria();
    DeleteResult deleteResult = DeleteResult.acknowledged(1L);

    // Exception
    when(mongoTemplate.remove((Query) any(Query.class), eq(TriggerEventHistory.class)))
        .thenThrow(new InvalidRequestException("message"));
    assertThatThrownBy(
        () -> triggerEventHistoryRepositoryCustom.deleteTriggerEventHistoryForTriggerIdentifier(criteria))
        .hasMessage("message")
        .isInstanceOf(InvalidRequestException.class);

    // Without exception
    when(mongoTemplate.remove((Query) any(Query.class), eq(TriggerEventHistory.class))).thenReturn(deleteResult);
    assertThat(
        triggerEventHistoryRepositoryCustom.deleteTriggerEventHistoryForTriggerIdentifier(criteria).wasAcknowledged())
        .isEqualTo(true);
  }
}
