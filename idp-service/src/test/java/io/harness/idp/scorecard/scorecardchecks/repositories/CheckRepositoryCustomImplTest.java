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
import io.harness.idp.scorecard.scorecardchecks.entity.CheckEntity;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.IDP)
public class CheckRepositoryCustomImplTest extends CategoryTest {
  @InjectMocks CheckRepositoryCustomImpl checkRepositoryCustomImpl;
  @Mock private MongoTemplate mongoTemplate;
  private static final String ACCOUNT_ID = "123";
  private static final String GITHUB_CHECK_NAME = "Github Checks";
  private static final String GITHUB_CHECK_NAME_UPDATED = "Github Checks Updated";
  private static final String GITHUB_CHECK_ID = "github_checks";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindAll() {
    when(mongoTemplate.find(any(), any())).thenReturn(List.of(getCheckEntity()));
    when(mongoTemplate.count(any(), eq(CheckEntity.class))).thenReturn(1l);
    Page<CheckEntity> checkEntityPage = checkRepositoryCustomImpl.findAll(
        Criteria.where(CheckEntity.CheckKeys.accountIdentifier).is(ACCOUNT_ID), PageUtils.getPageRequest(0, 10, null));
    assertEquals(1, checkEntityPage.getTotalElements());
    assertEquals(1, checkEntityPage.getContent().size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdate() {
    CheckEntity updatedEntity = getCheckEntity();
    updatedEntity.setName(GITHUB_CHECK_NAME_UPDATED);
    when(mongoTemplate.findAndModify(any(), any(), any(), eq(CheckEntity.class))).thenReturn(updatedEntity);
    CheckEntity entity = checkRepositoryCustomImpl.update(updatedEntity);
    assertEquals(GITHUB_CHECK_NAME_UPDATED, entity.getName());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateDeleted() {
    when(mongoTemplate.updateFirst(any(), any(), eq(CheckEntity.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1l, null));
    UpdateResult updateResult = checkRepositoryCustomImpl.updateDeleted(ACCOUNT_ID, GITHUB_CHECK_ID);
    assertEquals(1, updateResult.getMatchedCount());
  }

  private CheckEntity getCheckEntity() {
    return CheckEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(GITHUB_CHECK_ID)
        .name(GITHUB_CHECK_NAME)
        .isCustom(true)
        .build();
  }
}
