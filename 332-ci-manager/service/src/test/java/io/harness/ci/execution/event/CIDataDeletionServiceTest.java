/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.event;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.event.CIAccountDataStatus;
import io.harness.ci.event.CIDataDeletionService;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.repositories.CIAccountDataStatusRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CI)
public class CIDataDeletionServiceTest extends CIExecutionTestBase {
  private static final long TWELVE_HOURS_MINUS_TEN_MINUTES = 42600000;
  @Mock PersistentLocker persistentLocker;
  @Mock CIAccountDataStatusRepository ciAccountDataStatusRepository;
  @Mock ExecutorService executorService;
  @InjectMocks CIDataDeletionService ciDataDeletionService;

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testDeleteJob() {
    when(persistentLocker.tryToAcquireLock(any(), any())).thenReturn(mock(AcquiredLock.class));
    List<CIAccountDataStatus> list = new ArrayList<>();
    list.add(CIAccountDataStatus.builder().accountId("acc1").build());
    list.add(CIAccountDataStatus.builder()
                 .accountId("acc2")
                 .lastSent(System.currentTimeMillis() - TWELVE_HOURS_MINUS_TEN_MINUTES)
                 .build());
    list.add(CIAccountDataStatus.builder().accountId("acc3").lastSent(System.currentTimeMillis() - 1500).build());
    when(ciAccountDataStatusRepository.findAllByDeleted(any())).thenReturn(list);
    ciDataDeletionService.deleteJob();
    assertThat(list.get(0).getLastSent() != null).isTrue();
    assertThat(list.get(1).getLastSent() - System.currentTimeMillis() < TWELVE_HOURS_MINUS_TEN_MINUTES).isTrue();
    assertThat(list.get(2).getLastSent() - System.currentTimeMillis() < TWELVE_HOURS_MINUS_TEN_MINUTES).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testDeleteJobWithoutLock() {
    ciDataDeletionService.deleteJob();
  }
}
