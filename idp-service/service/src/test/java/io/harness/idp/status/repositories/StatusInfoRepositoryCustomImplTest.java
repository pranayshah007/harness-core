/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.repositories;

import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.status.enums.StatusType;
import io.harness.spec.server.idp.v1.model.StatusInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
public class StatusInfoRepositoryCustomImplTest {
  @InjectMocks private StatusInfoRepositoryCustomImpl statusInfoRepositoryCustomImpl;
  @Mock private MongoTemplate mongoTemplate;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Category(UnitTests.class)
  public void testSaveOrUpdateStatusInfo() {
    StatusInfoEntity statusInfoEntity = initializeStatusInfoEntity();
    when(mongoTemplate.findOne(any(Query.class), eq(StatusInfoEntity.class))).thenReturn(null);
    when(mongoTemplate.save(any(StatusInfoEntity.class))).thenReturn(statusInfoEntity);
    StatusInfoEntity entity = statusInfoRepositoryCustomImpl.saveOrUpdate(statusInfoEntity);
    assertNotNull(entity);

    when(mongoTemplate.findOne(any(Query.class), eq(StatusInfoEntity.class))).thenReturn(statusInfoEntity);
    when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(StatusInfoEntity.class)))
        .thenReturn(statusInfoEntity);
    entity = statusInfoRepositoryCustomImpl.saveOrUpdate(statusInfoEntity);
    assertNotNull(entity);
  }

  StatusInfoEntity initializeStatusInfoEntity() {
    return StatusInfoEntity.builder()
        .type(StatusType.ONBOARDING)
        .status(StatusInfo.CurrentStatusEnum.COMPLETED)
        .reason("completed successfully")
        .lastModifiedAt(System.currentTimeMillis())
        .build();
  }
}
