/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.audit;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditRecord;
import software.wings.service.intfc.AuditService;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class EntityAuditRecordHandlerTest extends WingsBaseTest {
  @Mock private AuditService auditService = mock(AuditService.class);

  @Inject private EntityAuditRecordHandler entityAuditRecordHandler;

  private AuditRecord entity;

  @Before
  public void setup() {
    entity = AuditRecord.builder()
                 .uuid("uuid")
                 .nextIteration(1L)
                 .auditHeaderId("auditHeaderId")
                 .accountId("accountId")
                 .createdAt(1L)
                 .build();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testHandleWithNullMostRecentAuditRecord() {
    AuditRecord mostRecentAuditRecord =
        AuditRecord.builder().auditHeaderId("auditHeaderId").accountId("accountId").build();
    mostRecentAuditRecord.setUuid(null);
    when(auditService.fetchMostRecentAuditRecord(anyString())).thenReturn(null);
    entityAuditRecordHandler.handle(entity);
    verify(auditService, atMost(1))
        .fetchLimitedEntityAuditRecordsOlderThanGivenTime(entity.getAuditHeaderId(), entity.getCreatedAt(), 1);
  }
}
