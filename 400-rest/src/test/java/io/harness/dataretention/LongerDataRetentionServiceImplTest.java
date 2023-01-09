/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.lock.PersistentLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.datatretention.LongerDataRetentionState;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LongerDataRetentionServiceImplTest extends WingsBaseTest {
  @Inject WingsPersistence wingsPersistence;
  @Mock PersistentLocker persistentLocker;
  @Inject @InjectMocks LongerDataRetentionServiceImpl longerDataRetentionService;

  @Before
  public void doSetup() {
    MockitoAnnotations.initMocks(this);
    doReturn(RedisAcquiredLock.builder().build()).when(persistentLocker).tryToAcquireLock(any(), any());
  }

  @org.junit.Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testLongerDataRetentionState() throws IllegalAccessException {
    String accountId = "acc";
    boolean longerDataRetentionCompleted = longerDataRetentionService.isLongerDataRetentionCompleted(
        LongerDataRetentionState.DEPLOYMENT_LONGER_RETENTION, accountId);
    assertThat(longerDataRetentionCompleted).isFalse();

    longerDataRetentionService.updateLongerDataRetentionState(
        LongerDataRetentionState.DEPLOYMENT_LONGER_RETENTION, true, accountId);
    boolean longerDataRetentionCompleted1 = longerDataRetentionService.isLongerDataRetentionCompleted(
        LongerDataRetentionState.DEPLOYMENT_LONGER_RETENTION, accountId);
    assertThat(longerDataRetentionCompleted1).isTrue();
    boolean longerDataRetentionCompleted2 = longerDataRetentionService.isLongerDataRetentionCompleted(
        LongerDataRetentionState.INSTANCE_LONGER_RETENTION, accountId);
    assertThat(longerDataRetentionCompleted2).isFalse();

    longerDataRetentionService.updateLongerDataRetentionState(
        LongerDataRetentionState.INSTANCE_LONGER_RETENTION, true, accountId);
    boolean longerDataRetentionCompleted3 = longerDataRetentionService.isLongerDataRetentionCompleted(
        LongerDataRetentionState.INSTANCE_LONGER_RETENTION, accountId);
    boolean longerDataRetentionCompleted4 = longerDataRetentionService.isLongerDataRetentionCompleted(
        LongerDataRetentionState.DEPLOYMENT_LONGER_RETENTION, accountId);
    assertThat(longerDataRetentionCompleted3).isTrue();
    assertThat(longerDataRetentionCompleted4).isTrue();
  }
}
