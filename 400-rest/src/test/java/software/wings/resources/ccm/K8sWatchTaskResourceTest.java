/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.K8sEventCollectionBundle;
import io.harness.ccm.ngperpetualtask.service.K8sWatchTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
public class K8sWatchTaskResourceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String TASK_ID = "taskId";

  @Mock K8sWatchTaskService k8sWatchTaskService;
  @InjectMocks K8sWatchTaskResource k8sWatchTaskResource;

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void create() throws Exception {
    when(k8sWatchTaskService.create(eq(ACCOUNT_ID), any())).thenReturn(TASK_ID);

    assertThat(k8sWatchTaskResource.create(ACCOUNT_ID, K8sEventCollectionBundle.builder().build()).getData())
        .isEqualTo(TASK_ID);

    verify(k8sWatchTaskService, times(1)).create(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void reset() throws Exception {
    when(k8sWatchTaskService.resetTask(eq(ACCOUNT_ID), eq(TASK_ID), any())).thenReturn(true);

    assertThat(k8sWatchTaskResource.reset(ACCOUNT_ID, TASK_ID, K8sEventCollectionBundle.builder().build()).getData())
        .isEqualTo(true);

    verify(k8sWatchTaskService, times(1)).resetTask(any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void delete() throws Exception {
    when(k8sWatchTaskService.delete(eq(ACCOUNT_ID), eq(TASK_ID))).thenReturn(true);

    assertThat(k8sWatchTaskResource.delete(ACCOUNT_ID, TASK_ID).getData()).isEqualTo(true);

    verify(k8sWatchTaskService, times(1)).delete(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void status() throws Exception {
    when(k8sWatchTaskService.getStatus(eq(TASK_ID))).thenReturn(PerpetualTaskRecord.builder().uuid("id0").build());

    assertThat(k8sWatchTaskResource.status(TASK_ID).getData().getUuid()).isEqualTo("id0");

    verify(k8sWatchTaskService, times(1)).getStatus(any());
  }
}
