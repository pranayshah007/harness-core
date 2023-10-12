/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateTaskLimitTest extends WingsBaseTest {
  @Inject private DelegateCacheImpl delegateCache;
  public static final String ACCOUNT_ID = "accountId";
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testTaskCountForPerAccountLimitForImportantTask() {
    createDelegateTask(DelegateTask.Status.QUEUED, DelegateTaskRank.IMPORTANT);
    createDelegateTask(DelegateTask.Status.STARTED, DelegateTaskRank.IMPORTANT);
    createDelegateTask(DelegateTask.Status.ABORTED, DelegateTaskRank.IMPORTANT);
    createDelegateTask(DelegateTask.Status.PARKED, DelegateTaskRank.IMPORTANT);
    createDelegateTask(DelegateTask.Status.QUEUED, DelegateTaskRank.IMPORTANT);
    assertThat(delegateCache.populateDelegateTaskCount(ACCOUNT_ID, DelegateTaskRank.IMPORTANT)).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testTaskCountForPerAccountLimitForOptionalTask() {
    createDelegateTask(DelegateTask.Status.QUEUED, DelegateTaskRank.OPTIONAL);
    createDelegateTask(DelegateTask.Status.STARTED, DelegateTaskRank.OPTIONAL);
    createDelegateTask(DelegateTask.Status.PARKED, DelegateTaskRank.OPTIONAL);
    assertThat(delegateCache.populateDelegateTaskCount(ACCOUNT_ID, DelegateTaskRank.OPTIONAL)).isEqualTo(2);
  }

  private void createDelegateTask(DelegateTask.Status status, DelegateTaskRank delegateTaskRank) {
    persistence.save(DelegateTask.builder().accountId(ACCOUNT_ID).status(status).rank(delegateTaskRank).build());
  }
}
