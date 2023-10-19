/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.event;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.InfraResourceDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.event.CIInfraCleanupService;
import io.harness.ci.execution.execution.StageCleanupUtility;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class CIInfraCleanupServiceTest extends CIExecutionTestBase {
  @Mock HPersistence persistence;
  @Mock KryoSerializer kryoSerializer;
  @Mock StageCleanupUtility stageCleanupUtility;
  @Mock Query<CIResourceCleanup> mockQuery;
  @Mock UpdateOperations<CIResourceCleanup> mockUpdateOperations;
  @InjectMocks CIInfraCleanupService ciInfraCleanupService;
  InfraResourceDetails infraResourceDetails;
  CIResourceCleanup ciResourceCleanup;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    infraResourceDetails = InfraResourceDetails.builder().build();
    ciResourceCleanup = CIResourceCleanup.builder().build();

    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(persistence.createUpdateOperations(CIResourceCleanup.class)).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.set(anyString(), any())).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.inc(anyString(), any())).thenReturn(mockUpdateOperations);
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(mockQuery.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.lessThan(any())).thenReturn(mockQuery);
    when(fieldEnd.equal(any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNoDocumentFound() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(null);
    ciInfraCleanupService.run();
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(0)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testResourceDetailsNotFound() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(null);
    ciInfraCleanupService.run();
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(1)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testExpiredDocuments() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setRetryCount(11);
    ciInfraCleanupService.run();
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(1)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any());
    ciResourceCleanup.setRetryCount(0);
    ciResourceCleanup.setCreatedAt(0);
    ciInfraCleanupService.run();
    verify(persistence, times(2)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(2)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(0)).submitCleanupRequest(any(), any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testEligibleDocuments() throws InterruptedException {
    when(persistence.findAndModify(any(), any(), any())).thenReturn(ciResourceCleanup);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(infraResourceDetails);
    ciResourceCleanup.setCreatedAt(System.currentTimeMillis());
    ciInfraCleanupService.run();
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
    verify(kryoSerializer, times(1)).asObject((byte[]) any());
    verify(stageCleanupUtility, times(1)).submitCleanupRequest(any(), any());
  }
}
