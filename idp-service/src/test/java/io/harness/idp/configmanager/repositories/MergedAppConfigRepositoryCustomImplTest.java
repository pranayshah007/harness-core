/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.repositories;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class MergedAppConfigRepositoryCustomImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock MongoTemplate mongoTemplate;

  @InjectMocks MergedAppConfigRepositoryCustomImpl mergedAppConfigRepositoryCustomImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ID = "test-id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  static final String TEST_CONFIG = "test-config";
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveOrUpdateConfigs() {
    MergedAppConfigEntity mergedAppConfigEntity = getTestMergedAppConfigEntity();
    when(mongoTemplate.findOne(any(Query.class), eq(MergedAppConfigEntity.class))).thenReturn(null);
    when(mongoTemplate.save(any(MergedAppConfigEntity.class))).thenReturn(mergedAppConfigEntity);
    assertEquals(mergedAppConfigEntity, mergedAppConfigRepositoryCustomImpl.saveOrUpdate(mergedAppConfigEntity));

    when(mongoTemplate.findOne(any(Query.class), eq(MergedAppConfigEntity.class))).thenReturn(mergedAppConfigEntity);
    when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(MergedAppConfigEntity.class)))
        .thenReturn(mergedAppConfigEntity);
    assertEquals(mergedAppConfigEntity, mergedAppConfigRepositoryCustomImpl.saveOrUpdate(mergedAppConfigEntity));
  }

  private MergedAppConfigEntity getTestMergedAppConfigEntity() {
    return MergedAppConfigEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .config(TEST_CONFIG)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .build();
  }
}
