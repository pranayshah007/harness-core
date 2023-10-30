/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecardchecks.repositories;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scorecardchecks.entity.ScorecardEntity;
import io.harness.rule.Owner;

import com.mongodb.client.result.DeleteResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardRepositoryCustomImplTest extends CategoryTest {
  @InjectMocks ScorecardRepositoryCustomImpl scorecardRepositoryCustomImpl;
  @Mock private MongoTemplate mongoTemplate;
  private static final String ACCOUNT_ID = "123";
  private static final String SCORECARD_ID = "service_maturity";
  private static final String SCORECARD_NAME = "Service Maturity";
  private static final String SCORECARD_NAME_UPDATED = "Service Maturity Update";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveOrUpdate() {
    ScorecardEntity scorecardEntity1 = getScorecardEntity();
    ScorecardEntity scorecardEntity2 = getScorecardEntity();
    scorecardEntity2.setName(SCORECARD_NAME_UPDATED);
    when(mongoTemplate.findOne(any(), eq(ScorecardEntity.class))).thenReturn(null).thenReturn(scorecardEntity2);
    when(mongoTemplate.save(any())).thenReturn(scorecardEntity1);
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(ScorecardEntity.class))).thenReturn(scorecardEntity2);

    ScorecardEntity savedEntity = scorecardRepositoryCustomImpl.saveOrUpdate(scorecardEntity1);
    assertEquals(SCORECARD_NAME, savedEntity.getName());

    savedEntity = scorecardRepositoryCustomImpl.saveOrUpdate(scorecardEntity2);
    assertEquals(SCORECARD_NAME_UPDATED, savedEntity.getName());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDelete() {
    when(mongoTemplate.remove(any(), eq(ScorecardEntity.class))).thenReturn(DeleteResult.acknowledged(1));
    DeleteResult deleteResult = scorecardRepositoryCustomImpl.delete(ACCOUNT_ID, SCORECARD_ID);
    assertEquals(1, deleteResult.getDeletedCount());
  }

  private ScorecardEntity getScorecardEntity() {
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .name(SCORECARD_NAME)
        .identifier(SCORECARD_ID)
        .published(true)
        .build();
  }
}
