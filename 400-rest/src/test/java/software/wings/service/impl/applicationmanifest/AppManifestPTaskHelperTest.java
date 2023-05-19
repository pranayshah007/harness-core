/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.applicationmanifest;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.PERPETUAL_TASK_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.manifests.request.ManifestCollectionPTaskClientParams.ManifestCollectionPTaskClientParamsKeys;
import io.harness.exception.GeneralException;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.service.intfc.ApplicationManifestService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AppManifestPTaskHelperTest extends CategoryTest {
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private ApplicationManifestService applicationManifestService;
  @Inject @InjectMocks private AppManifestPTaskHelper appManifestPTaskHelper;

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void testCreatePerpetualTask() {
    ApplicationManifest appManifest = buildAppManifest();
    ArgumentCaptor<PerpetualTaskClientContext> clientContextCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    ArgumentCaptor<PerpetualTaskSchedule> scheduleCaptor = ArgumentCaptor.forClass(PerpetualTaskSchedule.class);

    when(applicationManifestService.attachPerpetualTask(ACCOUNT_ID, MANIFEST_ID, PERPETUAL_TASK_ID)).thenReturn(true);
    when(perpetualTaskService.createTask(eq(PerpetualTaskType.MANIFEST_COLLECTION), eq(ACCOUNT_ID),
             clientContextCaptor.capture(), scheduleCaptor.capture(), eq(false), anyString()))
        .thenReturn(PERPETUAL_TASK_ID);
    appManifestPTaskHelper.createPerpetualTask(appManifest);

    assertThat(
        clientContextCaptor.getValue().getClientParams().get(ManifestCollectionPTaskClientParamsKeys.appManifestId))
        .isEqualTo(MANIFEST_ID);
    assertThat(scheduleCaptor.getValue().getInterval()).isEqualTo(AppManifestPTaskHelper.ITERATION_INTERVAL);
    assertThat(scheduleCaptor.getValue().getTimeout()).isEqualTo(AppManifestPTaskHelper.TIMEOUT);

    when(applicationManifestService.attachPerpetualTask(ACCOUNT_ID, MANIFEST_ID, PERPETUAL_TASK_ID)).thenReturn(false);
    appManifestPTaskHelper.createPerpetualTask(appManifest);
    verify(perpetualTaskService, times(1)).deleteTask(ACCOUNT_ID, PERPETUAL_TASK_ID);
    verify(applicationManifestService, times(1)).detachPerpetualTask(PERPETUAL_TASK_ID, ACCOUNT_ID);

    appManifest.setUuid(null);
    assertThatThrownBy(() -> appManifestPTaskHelper.createPerpetualTask(appManifest))
        .isInstanceOf(GeneralException.class);
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void testDeletePerpetualTask() {
    ApplicationManifest applicationManifest = buildAppManifest();
    appManifestPTaskHelper.deletePerpetualTask(
        PERPETUAL_TASK_ID, applicationManifest.getUuid(), applicationManifest.getAccountId());
    verify(perpetualTaskService, times(1)).deleteTask(ACCOUNT_ID, PERPETUAL_TASK_ID);
    verify(applicationManifestService, times(1)).detachPerpetualTask(PERPETUAL_TASK_ID, ACCOUNT_ID);
  }

  private ApplicationManifest buildAppManifest() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().accountId(ACCOUNT_ID).serviceId(SERVICE_ID).storeType(StoreType.Remote).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest.setUuid(MANIFEST_ID);
    return applicationManifest;
  }
}
