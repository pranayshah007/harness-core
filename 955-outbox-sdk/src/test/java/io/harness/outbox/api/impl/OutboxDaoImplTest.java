/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.outbox.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.outbox.OutboxEvent.OutboxEventKeys;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.rule.Owner;

import com.mongodb.BasicDBList;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class OutboxDaoImplTest extends CategoryTest {
  private OutboxEventRepository outboxEventRepository;
  private OutboxDaoImpl outboxDao;

  @Before
  public void setup() {
    outboxEventRepository = mock(OutboxEventRepository.class);
    outboxDao = new OutboxDaoImpl(outboxEventRepository);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    OutboxEventFilter outboxEventFilter = OutboxEventFilter.builder().maximumEventsPolled(50).build();
    final ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    final ArgumentCaptor<Pageable> pageableArgumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(outboxEventRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(emptyList());
    outboxDao.list(outboxEventFilter);
    verify(outboxEventRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), pageableArgumentCaptor.capture());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Pageable pageable = pageableArgumentCaptor.getValue();
    Document criteriaObject = criteria.getCriteriaObject();
    assertEquals(1, criteriaObject.size());
    BasicDBList orList = (BasicDBList) criteriaObject.get("$or");
    assertNotNull(orList);
    assertEquals(2, orList.size());

    Document blocked = (Document) orList.get(0);
    assertNotNull(blocked);
    Document blockedValue = (Document) blocked.get(OutboxEventKeys.blocked);
    assertTrue(blockedValue.getBoolean("$ne"));

    Document afterBlocked = (Document) orList.get(1);
    assertTrue(afterBlocked.getBoolean(OutboxEventKeys.blocked));
    Document lt = (Document) afterBlocked.get(OutboxEventKeys.nextUnblockAttemptAt);
    assertNotNull(lt.get("$lt"));

    assertEquals(50, pageable.getPageSize());
    assertEquals(0, pageable.getPageNumber());
    Sort sort = Sort.by(OutboxEventKeys.createdAt);
    assertEquals(sort, pageable.getSort());
  }
}
