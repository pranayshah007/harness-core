/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.repositories;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
public class UserEventRepositoryCustomImplTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = "example-account";

  @Mock private MongoTemplate mongoTemplate;

  @InjectMocks private UserEventRepositoryCustomImpl userEventRepositoryCustomImpl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSaveOrUpdateNewEntity() {
    UserEventEntity userEventEntity =
        UserEventEntity.builder().accountIdentifier(ACCOUNT_IDENTIFIER).hasEvent(true).build();
    when(mongoTemplate.save(userEventEntity)).thenReturn(userEventEntity);

    UserEventEntity result = userEventRepositoryCustomImpl.saveOrUpdate(userEventEntity);

    verify(mongoTemplate, times(1)).save(userEventEntity);
    assertEquals(userEventEntity, result);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSaveOrUpdateExistingEntity() {
    UserEventEntity userEventEntity =
        UserEventEntity.builder().accountIdentifier(ACCOUNT_IDENTIFIER).hasEvent(true).build();

    Criteria criteria =
        Criteria.where(UserEventEntity.UserEventKeys.accountIdentifier).is(userEventEntity.getAccountIdentifier());
    when(mongoTemplate.findOne(any(Query.class), eq(UserEventEntity.class))).thenReturn(userEventEntity);

    when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(UserEventEntity.class)))
        .thenReturn(userEventEntity);

    UserEventEntity result = userEventRepositoryCustomImpl.saveOrUpdate(userEventEntity);

    verify(mongoTemplate, never()).save(userEventEntity);
    verify(mongoTemplate, times(1)).findOne(any(Query.class), eq(UserEventEntity.class));
    verify(mongoTemplate, times(1))
        .findAndModify(any(Query.class), any(Update.class), any(), eq(UserEventEntity.class));
    assertEquals(userEventEntity, result);
  }
}
