/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.event;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.rule.OwnerRule.SERGEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.STOBeansTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.repositories.STOAccountDataStatusRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(STO)
public class STODataDeletionServiceTest extends STOBeansTestBase {
  private static final long TWELVE_HOURS_MINUS_TEN_MINUTES = 42600000;
  @Mock PersistentLocker persistentLocker;
  @Mock STOAccountDataStatusRepository stoAccountDataStatusRepository;
  @Mock ExecutorService executorService;
  @InjectMocks STODataDeletionService stoDataDeletionService;

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void testDeleteJob() {
    when(persistentLocker.tryToAcquireLock(any(), any())).thenReturn(mock(AcquiredLock.class));
    List<STOAccountDataStatus> list = new ArrayList<>();
    list.add(STOAccountDataStatus.builder().accountId("acc1").build());
    list.add(STOAccountDataStatus.builder()
                 .accountId("acc2")
                 .lastSent(System.currentTimeMillis() - TWELVE_HOURS_MINUS_TEN_MINUTES)
                 .build());
    list.add(STOAccountDataStatus.builder().accountId("acc3").lastSent(System.currentTimeMillis() - 1500).build());
    when(stoAccountDataStatusRepository.findAllByDeleted(any())).thenReturn(list);
    stoDataDeletionService.deleteJob();
    assertThat(list.get(0).getLastSent() != null).isTrue();
    assertThat(list.get(1).getLastSent() - System.currentTimeMillis() < TWELVE_HOURS_MINUS_TEN_MINUTES).isTrue();
    assertThat(list.get(2).getLastSent() - System.currentTimeMillis() < TWELVE_HOURS_MINUS_TEN_MINUTES).isTrue();
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void testDeleteJobWithoutLock() {
    stoDataDeletionService.deleteJob();
  }
}
