/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.jobs;

import static io.harness.rule.OwnerRule.SARVAGNYA_JATTI;

import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.idp.user.repositories.UserEventRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class UserSyncJobTest extends CategoryTest {
  private static final String TEST_ACCOUNT1 = "acc1";
  private static final String TEST_ACCOUNT2 = "acc2";
  @Mock private UserEventRepository userEventRepository;
  @Mock private BackstageResourceClient backstageResourceClient;
  @InjectMocks private UserSyncJob job;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SARVAGNYA_JATTI)
  @Category(UnitTests.class)
  public void testUserSync() {
    UserEventEntity acc1 = UserEventEntity.builder().accountIdentifier(TEST_ACCOUNT1).hasEvent(true).build();
    UserEventEntity acc2 = UserEventEntity.builder().accountIdentifier(TEST_ACCOUNT2).hasEvent(true).build();
    when(userEventRepository.findAllByHasEvent(true)).thenReturn(Arrays.asList(acc1, acc2));
    job.run();
    verify(backstageResourceClient).providerRefresh(acc1.getAccountIdentifier());
    verify(backstageResourceClient).providerRefresh(acc2.getAccountIdentifier());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
