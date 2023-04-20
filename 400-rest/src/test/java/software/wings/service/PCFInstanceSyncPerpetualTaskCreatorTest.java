/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.service.InstanceSyncConstants.INTERVAL_MINUTES;
import static software.wings.service.InstanceSyncConstants.TIMEOUT_SECONDS;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ORGANIZATION;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_APP_GUID_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_INSTANCE_INDEX_0;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SPACE;

import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.instancesync.PcfInstanceSyncPerpetualTaskClientParams;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentSummary;
import software.wings.api.PcfDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.beans.infrastructure.instance.key.PcfInstanceKey;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.protobuf.util.Durations;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
public class PCFInstanceSyncPerpetualTaskCreatorTest extends WingsBaseTest {
  public static final String ACCOUNT_ID = "accountId";
  private static final String TASK_ID = "taskId";
  private static final String APPLICATION_NAME = "applicationName";
  private static final String INFRA_ID = "infraId";
  private static final String APP_ID = "appId";
  private PcfInfrastructureMapping pcfInfrastructureMapping;

  @Mock PerpetualTaskService perpetualTaskService;
  @InjectMocks PCFInstanceSyncPerpetualTaskCreator pcfInstanceSyncPerpetualTaskCreator;
  @Captor ArgumentCaptor<PerpetualTaskSchedule> scheduleArgumentCaptor;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private InstanceService instanceService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(perpetualTaskService.createTask(
             any(), anyString(), any(), scheduleArgumentCaptor.capture(), eq(false), anyString()))
        .thenReturn(TASK_ID);
    pcfInfrastructureMapping = PcfInfrastructureMapping.builder()
                                   .uuid(INFRASTRUCTURE_MAPPING_ID)
                                   .accountId(ACCOUNT_ID)
                                   .name(INFRASTRUCTURE_MAPPING_ID)
                                   .appId(APP_ID)
                                   .build();
    pcfInfrastructureMapping.setDisplayName("infraName");
    when(infrastructureMappingService.get(any(), any())).thenReturn(pcfInfrastructureMapping);
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment().accountId(ACCOUNT_ID).appId(APP_ID).build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder().accountId(ACCOUNT_ID).appId(APP_ID).build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication().appId(APP_ID).accountId(ACCOUNT_ID).build());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testCreate() {
    PcfInstanceSyncPerpetualTaskClientParams pcfInstanceSyncParams = getPcfInstanceSyncPerpTaskClientParams();

    String taskId = pcfInstanceSyncPerpetualTaskCreator.create(pcfInstanceSyncParams, pcfInfrastructureMapping);
    PerpetualTaskSchedule taskSchedule = scheduleArgumentCaptor.getValue();

    assertThat(taskId).isEqualTo(TASK_ID);
    assertThat(taskSchedule.getInterval()).isEqualTo(Durations.fromMinutes(INTERVAL_MINUTES));
    assertThat(taskSchedule.getTimeout()).isEqualTo(Durations.fromSeconds(TIMEOUT_SECONDS));
    Mockito.verify(perpetualTaskService, Mockito.times(1))
        .createTask(any(), anyString(), any(), any(), eq(false), anyString());
  }

  private PcfInstanceSyncPerpetualTaskClientParams getPcfInstanceSyncPerpTaskClientParams() {
    return PcfInstanceSyncPerpetualTaskClientParams.builder()
        .appId(HARNESS_APPLICATION_ID)
        .inframappingId(INFRA_ID)
        .applicationName(APPLICATION_NAME)
        .build();
  }
  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void createPerpetualTaskForNewDeployment() {
    DeploymentSummary deploymentSummary =
        DeploymentSummary.builder()
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .infraMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfo(
                PcfDeploymentInfo.builder().applicationGuild("guid").applicationName(APPLICATION_NAME).build())
            .build();
    List<String> tasks = pcfInstanceSyncPerpetualTaskCreator.createPerpetualTasksForNewDeployment(
        singletonList(deploymentSummary), Collections.emptyList(), new PcfInfrastructureMapping());
    assertEquals(1, tasks.size());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void createCreatePerpetualTasks() {
    doReturn(getInstancesForAppAndInframapping())
        .when(instanceService)
        .getInstancesForAppAndInframapping(APP_ID, INFRASTRUCTURE_MAPPING_ID);

    List<String> tasks = pcfInstanceSyncPerpetualTaskCreator.createPerpetualTasks(pcfInfrastructureMapping);

    assertEquals(1, tasks.size());
    assertThat(tasks.get(0)).isEqualTo(TASK_ID);
  }

  private List<Instance> getInstancesForAppAndInframapping() {
    return singletonList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
            .pcfInstanceKey(PcfInstanceKey.builder().id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0).build())
            .instanceType(InstanceType.PCF_INSTANCE)
            .instanceInfo(PcfInstanceInfo.builder()
                              .organization(ORGANIZATION)
                              .space(SPACE)
                              .pcfApplicationName(APP_NAME_1)
                              .pcfApplicationGuid(PCF_APP_GUID_1)
                              .instanceIndex(PCF_INSTANCE_INDEX_0)
                              .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0)
                              .build())
            .build());
  }
}
