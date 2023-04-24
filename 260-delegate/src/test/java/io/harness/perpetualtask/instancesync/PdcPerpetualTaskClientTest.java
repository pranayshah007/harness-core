/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.HOSTNAMES;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingWinRm;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class PdcPerpetualTaskClientTest extends WingsBaseTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String TASK_ID = "taskId";
  private static final String INFRA_ID = "infraId";
  private static final String HOST_NAMES = "h1,h2";

  @Mock PerpetualTaskService perpetualTaskService;
  @Mock SettingsService settingsService;
  @Mock SecretManager secretsManager;
  @Mock InfrastructureMappingService infraMappingService;
  @InjectMocks @Inject PdcPerpetualTaskServiceClient pdcPerpetualTaskServiceClient;

  @Captor ArgumentCaptor<PerpetualTaskSchedule> scheduleArgumentCaptor;
  @Captor ArgumentCaptor<String> stringCaptor;

  @Before
  public void setUp() throws Exception {
    HostConnectionAttributes hostConnectionAttributes =
        HostConnectionAttributes.Builder.aHostConnectionAttributes().build();
    when(perpetualTaskService.deleteTask(any(), any())).thenReturn(true);
    when(perpetualTaskService.resetTask(stringCaptor.capture(), any(), isNull(PerpetualTaskExecutionBundle.class)))
        .thenReturn(true);
    when(perpetualTaskService.createTask(any(), any(), any(), scheduleArgumentCaptor.capture(), anyBoolean(), any()))
        .thenReturn(TASK_ID);
    when(infraMappingService.get(any(), any()))
        .thenReturn(PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping()
                        .withAppId(HARNESS_APPLICATION_ID)
                        .withUuid(INFRA_ID)
                        .build());
    when(settingsService.get(any()))
        .thenReturn(software.wings.beans.SettingAttribute.Builder.aSettingAttribute()
                        .withValue(hostConnectionAttributes)
                        .build());
    when(perpetualTaskService.getTaskRecord(TASK_ID))
        .thenReturn(PerpetualTaskRecord.builder().accountId(ACCOUNT_ID).build());
    when(secretsManager.getEncryptionDetails(hostConnectionAttributes, null, null)).thenReturn(Collections.emptyList());
  }

  private PerpetualTaskClientContext getPerpetualTaskClientContext() {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(INFRASTRUCTURE_MAPPING_ID, INFRA_ID);
    clientParamMap.put(HARNESS_APPLICATION_ID, HARNESS_APPLICATION_ID);
    clientParamMap.put(HOSTNAMES, HOST_NAMES);
    return PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void getValidationTask() {
    PerpetualTaskClientContext clientContext = getPerpetualTaskClientContext();
    DelegateTask validationTask = pdcPerpetualTaskServiceClient.getValidationTask(clientContext, ACCOUNT_ID);

    assertThat(validationTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(validationTask.getData().getTaskType()).isEqualTo(TaskType.HOST_VALIDATION.name());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetTaskParams() {
    PerpetualTaskClientContext clientContext = getPerpetualTaskClientContext();

    Message taskParams = pdcPerpetualTaskServiceClient.getTaskParams(clientContext);

    assertThat(taskParams).isNotNull();
    assertThat(taskParams instanceof PdcInstanceSyncPerpetualTaskParams).isTrue();

    PdcInstanceSyncPerpetualTaskParams syncPerpetualTaskParams = (PdcInstanceSyncPerpetualTaskParams) taskParams;
    assertThat(new ArrayList<>(syncPerpetualTaskParams.getHostNamesList())).isEqualTo(Arrays.asList("h1", "h2"));
    assertThat(syncPerpetualTaskParams.getEncryptedData()).isNotNull();
    assertThat(syncPerpetualTaskParams.getSettingAttribute()).isNotNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetTaskParamsWinrm() {
    PerpetualTaskClientContext clientContext = getPerpetualTaskClientContext();

    when(infraMappingService.get(any(), any()))
        .thenReturn(PhysicalInfrastructureMappingWinRm.Builder.aPhysicalInfrastructureMappingWinRm()
                        .withAppId(HARNESS_APPLICATION_ID)
                        .withUuid(INFRA_ID)
                        .build());
    WinRmConnectionAttributes winRmConnectionAttributes = WinRmConnectionAttributes.builder().build();
    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(winRmConnectionAttributes).build());
    when(secretsManager.getEncryptionDetails(winRmConnectionAttributes, null, null))
        .thenReturn(Collections.emptyList());

    Message taskParams = pdcPerpetualTaskServiceClient.getTaskParams(clientContext);

    assertThat(taskParams).isNotNull();
    assertThat(taskParams instanceof PdcInstanceSyncPerpetualTaskParams).isTrue();

    PdcInstanceSyncPerpetualTaskParams syncPerpetualTaskParams = (PdcInstanceSyncPerpetualTaskParams) taskParams;
    assertThat(new ArrayList<>(syncPerpetualTaskParams.getHostNamesList())).isEqualTo(Arrays.asList("h1", "h2"));
    assertThat(syncPerpetualTaskParams.getEncryptedData()).isNotNull();
    assertThat(syncPerpetualTaskParams.getSettingAttribute()).isNotNull();
  }
}
