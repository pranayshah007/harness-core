/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.repositories;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.entities.IDPTelemetrySentStatus;
import io.harness.idp.license.usage.entities.IDPTelemetrySentStatus.IDPTelemetrySentStatusKeys;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPTelemetryStatusRepositoryCustomImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";

  AutoCloseable openMocks;
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks IDPTelemetryStatusRepositoryCustomImpl idpTelemetryStatusRepositoryCustom;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateTimestampIfOlderThanUpdateSuccess() {
    long olderThanTime = System.currentTimeMillis();
    long updateToTime = System.currentTimeMillis();

    IDPTelemetrySentStatus idpTelemetrySentStatus =
        IDPTelemetrySentStatus.builder().accountId(TEST_ACCOUNT_IDENTIFIER).lastSent(updateToTime).build();

    Criteria criteria = new Criteria()
                            .and(IDPTelemetrySentStatusKeys.accountId)
                            .is(TEST_ACCOUNT_IDENTIFIER)
                            .and(IDPTelemetrySentStatusKeys.lastSent)
                            .lte(olderThanTime);
    Query query = new Query().addCriteria(criteria);
    Update update = new Update().set(IDPTelemetrySentStatusKeys.lastSent, updateToTime);

    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(IDPTelemetrySentStatus.class)))
        .thenReturn(idpTelemetrySentStatus);

    assertTrue(idpTelemetryStatusRepositoryCustom.updateTimestampIfOlderThan(
        TEST_ACCOUNT_IDENTIFIER, olderThanTime, updateToTime));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateTimestampIfOlderThanUpdateFailed() {
    long olderThanTime = System.currentTimeMillis();
    long updateToTime = System.currentTimeMillis();

    Criteria criteria = new Criteria()
                            .and(IDPTelemetrySentStatusKeys.accountId)
                            .is(TEST_ACCOUNT_IDENTIFIER)
                            .and(IDPTelemetrySentStatusKeys.lastSent)
                            .lte(olderThanTime);
    Query query = new Query().addCriteria(criteria);
    Update update = new Update().set(IDPTelemetrySentStatusKeys.lastSent, updateToTime);

    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(IDPTelemetrySentStatus.class)))
        .thenReturn(null);

    assertFalse(idpTelemetryStatusRepositoryCustom.updateTimestampIfOlderThan(
        TEST_ACCOUNT_IDENTIFIER, olderThanTime, updateToTime));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateTimestampIfOlderThanThrowsDuplicateAliasException() {
    long olderThanTime = System.currentTimeMillis();
    long updateToTime = System.currentTimeMillis();

    Criteria criteria = new Criteria()
                            .and(IDPTelemetrySentStatusKeys.accountId)
                            .is(TEST_ACCOUNT_IDENTIFIER)
                            .and(IDPTelemetrySentStatusKeys.lastSent)
                            .lte(olderThanTime);
    Query query = new Query().addCriteria(criteria);
    Update update = new Update().set(IDPTelemetrySentStatusKeys.lastSent, updateToTime);

    when(mongoTemplate.findAndModify(
             eq(query), eq(update), any(FindAndModifyOptions.class), eq(IDPTelemetrySentStatus.class)))
        .thenThrow(DuplicateKeyException.class);

    assertFalse(idpTelemetryStatusRepositoryCustom.updateTimestampIfOlderThan(
        TEST_ACCOUNT_IDENTIFIER, olderThanTime, updateToTime));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
