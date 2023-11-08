/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.repositories;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.checks.repositories.CheckStatusEntityByIdentifier;
import io.harness.idp.scorecard.checks.repositories.CheckStatusRepositoryCustomImpl;
import io.harness.rule.Owner;

import java.util.List;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@OwnedBy(HarnessTeam.IDP)
public class CheckStatusRepositoryCustomImplTest extends CategoryTest {
  @InjectMocks CheckStatusRepositoryCustomImpl checkStatusRepositoryCustomImpl;
  @Mock private MongoTemplate mongoTemplate;
  private static final String ACCOUNT_ID = "123";
  private static final String GITHUB_CHECK_ID = "github_checks";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndIdentifierIn() {
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("checkStatus"), eq(CheckStatusEntityByIdentifier.class)))
        .thenReturn(new AggregationResults<>(List.of(CheckStatusEntityByIdentifier.builder()
                                                         .identifier(GITHUB_CHECK_ID)
                                                         .custom(true)
                                                         .checkStatusEntity(CheckStatusEntity.builder().build())
                                                         .build()),
            new Document()));
    List<CheckStatusEntityByIdentifier> checkStatusEntityByIdentifiers =
        checkStatusRepositoryCustomImpl.findByAccountIdentifierAndIdentifierIn(ACCOUNT_ID, List.of(GITHUB_CHECK_ID));
    assertThat(checkStatusEntityByIdentifiers.size()).isEqualTo(1);
    assertThat(checkStatusEntityByIdentifiers.get(0).getIdentifier()).isEqualTo(GITHUB_CHECK_ID);
  }
}
