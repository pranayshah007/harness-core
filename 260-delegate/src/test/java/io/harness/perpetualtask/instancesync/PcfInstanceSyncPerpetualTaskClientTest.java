/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.beans.TaskType.PCF_COMMAND_TASK;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
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
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.protobuf.Message;
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
public class PcfInstanceSyncPerpetualTaskClientTest extends WingsBaseTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String TASK_ID = "taskId";
  private static final String APPLICATION_NAME = "applicationName";
  private static final String INFRA_ID = "infraId";
  private static final String ORG_NAME = "orgName";
  private static final String SPACE = "space";

  @Mock PerpetualTaskService perpetualTaskService;
  @Mock SettingsService settingsService;
  @Mock SecretManager secretsManager;
  @Mock InfrastructureMappingService infraMappingService;
  @Inject KryoSerializer kryoSerializer;
  @InjectMocks @Inject PcfInstanceSyncPerpetualTaskClient pcfInstanceSyncPerpetualTaskClient;

  @Captor ArgumentCaptor<PerpetualTaskSchedule> scheduleArgumentCaptor;
  @Captor ArgumentCaptor<String> stringCaptor;

  @Before
  public void setUp() throws Exception {
    PcfConfig pcfConfig = PcfConfig.builder().accountId(ACCOUNT_ID).build();
    when(perpetualTaskService.deleteTask(any(), any())).thenReturn(true);
    when(perpetualTaskService.resetTask(stringCaptor.capture(), any(), isNull(PerpetualTaskExecutionBundle.class)))
        .thenReturn(true);
    when(perpetualTaskService.createTask(
             any(), anyString(), any(), scheduleArgumentCaptor.capture(), anyBoolean(), anyString()))
        .thenReturn(TASK_ID);
    when(infraMappingService.get(anyString(), anyString()))
        .thenReturn(PcfInfrastructureMapping.builder()
                        .appId(HARNESS_APPLICATION_ID)
                        .uuid(INFRA_ID)
                        .organization(ORG_NAME)
                        .space(SPACE)
                        .build());
    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(pcfConfig).build());
    when(perpetualTaskService.getTaskRecord(TASK_ID))
        .thenReturn(PerpetualTaskRecord.builder().accountId(ACCOUNT_ID).build());
    when(secretsManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
  }

  private PerpetualTaskClientContext getPerpetualTaskClientContext() {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(INFRASTRUCTURE_MAPPING_ID, INFRA_ID);
    clientParamMap.put(HARNESS_APPLICATION_ID, HARNESS_APPLICATION_ID);
    clientParamMap.put(PcfInstanceSyncPerpetualTaskClient.PCF_APPLICATION_NAME, APPLICATION_NAME);
    return PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getValidationTask() {
    PerpetualTaskClientContext clientContext = getPerpetualTaskClientContext();
    DelegateTask validationTask = pcfInstanceSyncPerpetualTaskClient.getValidationTask(clientContext, ACCOUNT_ID);

    assertThat(validationTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(validationTask.getData().getTaskType()).isEqualTo(PCF_COMMAND_TASK.name());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetTaskParams() {
    PerpetualTaskClientContext clientContext = getPerpetualTaskClientContext();

    Message taskParams = pcfInstanceSyncPerpetualTaskClient.getTaskParams(clientContext);

    assertThat(taskParams).isNotNull();
    assertThat(taskParams instanceof PcfInstanceSyncPerpetualTaskParams).isTrue();

    PcfInstanceSyncPerpetualTaskParams syncPerpetualTaskParams = (PcfInstanceSyncPerpetualTaskParams) taskParams;
    assertThat(syncPerpetualTaskParams.getApplicationName()).isEqualTo(APPLICATION_NAME);
    assertThat(syncPerpetualTaskParams.getInfraMappingId()).isEqualTo(INFRA_ID);
    assertThat(syncPerpetualTaskParams.getOrgName()).isEqualTo(ORG_NAME);
    assertThat(syncPerpetualTaskParams.getSpace()).isEqualTo(SPACE);
    assertThat(syncPerpetualTaskParams.getPcfConfig()).isNotNull();
    assertThat(syncPerpetualTaskParams.getEncryptedData()).isNotNull();
  }
}
