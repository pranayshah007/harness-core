/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.MongoConfig;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.DeleteResult;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.CDC)
public class PopulateTTLFieldAndDeleteOldInApprovalInstancesMigrationTest extends PipelineServiceTestBase {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private BulkOperations bulkOperations;
  @Mock private BulkWriteResult bulkWriteResult;
  @Mock private CloseableIterator closeableIterator;
  @Mock private DeleteResult deleteResult;
  @InjectMocks PopulateTTLFieldAndDeleteOldInApprovalInstancesMigration migration;
  private static final Long createdAt = 1650170397000L;
  private static final Date validUntil = Date.from(
      Instant.ofEpochMilli(createdAt).atOffset(ZoneOffset.UTC).plusMonths(ApprovalInstance.TTL_MONTHS).toInstant());

  private static final String identifier = "id";

  @Before
  public void setup() {
    when(mongoTemplate.bulkOps(any(), eq(ApprovalInstance.class))).thenReturn(bulkOperations);
    when(mongoTemplate.stream(any(), any())).thenReturn(closeableIterator);
    when(bulkOperations.execute()).thenReturn(bulkWriteResult);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMigrate() {
    when(closeableIterator.next()).thenReturn(buildApprovalInstance(false));

    when(closeableIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(mongoTemplate.remove(any(), eq(ApprovalInstance.class))).thenReturn(deleteResult);
    when(deleteResult.getDeletedCount()).thenReturn(10L);
    when(bulkWriteResult.getModifiedCount()).thenReturn(1);
    migration.migrate();

    Criteria idCriteria = Criteria.where(ApprovalInstanceKeys.id).is(identifier);

    Update update = new Update();
    update.set(ApprovalInstanceKeys.validUntil, validUntil);
    verify(mongoTemplate, times(1))
        .remove(new Query(Criteria.where(ApprovalInstanceKeys.createdAt).lt(createdAt)).limit(MongoConfig.NO_LIMIT),
            ApprovalInstance.class);
    verify(deleteResult, times(1)).getDeletedCount();
    verify(bulkOperations, times(1)).updateOne(new Query(idCriteria), update);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMigrateShouldBeIdemPotent() {
    assertThat(buildApprovalInstance(true).getValidUntil()).isNotNull();
    when(closeableIterator.next()).thenReturn(buildApprovalInstance(true));

    when(closeableIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(mongoTemplate.remove(any(), eq(ApprovalInstance.class))).thenReturn(deleteResult);
    when(deleteResult.getDeletedCount()).thenReturn(10L);

    migration.migrate();
    verify(mongoTemplate, times(1))
        .remove(new Query(Criteria.where(ApprovalInstanceKeys.createdAt).lt(createdAt)).limit(MongoConfig.NO_LIMIT),
            ApprovalInstance.class);
    verify(deleteResult, times(1)).getDeletedCount();
    verify(mongoTemplate, times(1)).stream(any(), any());
    verifyNoMoreInteractions(bulkOperations);
  }

  private ApprovalInstance buildApprovalInstance(boolean withTTLField) {
    ApprovalInstance approvalInstance = HarnessApprovalInstance.builder().approvalMessage("dummy message").build();
    approvalInstance.setId(identifier);
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setStatus(ApprovalStatus.WAITING);
    approvalInstance.setNodeExecutionId("dummy nodeExeId");
    approvalInstance.setCreatedAt(createdAt);

    if (withTTLField) {
      approvalInstance.setValidUntil(validUntil);
    }
    return approvalInstance;
  }
}
