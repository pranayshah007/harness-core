/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.DelegateTaskBroadcast;

import com.google.inject.Inject;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DelegateTaskBroadcastHelperTest extends WingsBaseTest {
  @Mock private BroadcasterFactory broadcasterFactory;
  @Mock private FeatureFlagService featureFlagService;
  @InjectMocks @Inject private DelegateTaskBroadcastHelper broadcastHelper;

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testRebroadcastDelegateTask() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .version(generateUuid())
                                    .accountId(generateUuid())
                                    .uuid(generateUuid())
                                    .data(TaskData.builder().async(true).build())
                                    .build();

    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);

    delegateTask.setPreAssignedDelegateId(null);
    delegateTask.setAlreadyTriedDelegates(null);

    broadcastHelper.rebroadcastDelegateTask(delegateTask);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.isAsync()).isEqualTo(delegateTask.getData().isAsync());
    assertThat(delegateTaskBroadcast.getPreAssignedDelegateId()).isEqualTo(delegateTask.getPreAssignedDelegateId());
  }
}
