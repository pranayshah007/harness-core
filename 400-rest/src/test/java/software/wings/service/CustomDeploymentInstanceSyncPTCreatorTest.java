/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.InstanceSyncConstants.INTERVAL_MINUTES;
import static software.wings.service.InstanceSyncConstants.TIMEOUT_SECONDS;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.UUID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.api.DeploymentSummary;
import software.wings.beans.Application;
import software.wings.beans.CustomInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.protobuf.util.Durations;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CustomDeploymentInstanceSyncPTCreatorTest extends CategoryTest {
  @Mock private PerpetualTaskService perpetualTaskService;
  @InjectMocks private CustomDeploymentInstanceSyncPTCreator perpetualTaskCreator;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment()
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .name(ENV_NAME)
                        .build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder()
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .name(SERVICE_NAME)
                        .build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication()
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .name(APP_NAME)
                        .build());
  }

  ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);

  private final InfrastructureMapping infraMapping = buildInfraMapping();

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void createPerpetualTasks() {
    perpetualTaskCreator.createPerpetualTasks(infraMapping);

    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.CUSTOM_DEPLOYMENT_INSTANCE_SYNC), eq(ACCOUNT_ID), captor.capture(),
            eq(PerpetualTaskSchedule.newBuilder()
                    .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                    .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                    .build()),
            eq(false), anyString());

    verify(perpetualTaskService, never()).resetTask(anyString(), anyString(), any());
    assertionsForClientParams();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void shouldCreateIfNoTaskExists() {
    perpetualTaskCreator.createPerpetualTasksForNewDeployment(
        singletonList(DeploymentSummary.builder().build()), null, infraMapping);

    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.CUSTOM_DEPLOYMENT_INSTANCE_SYNC), eq(ACCOUNT_ID), captor.capture(),
            eq(PerpetualTaskSchedule.newBuilder()
                    .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                    .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                    .build()),
            eq(false), anyString());

    verify(perpetualTaskService, never()).resetTask(anyString(), anyString(), any());
    assertionsForClientParams();
  }

  private void assertionsForClientParams() {
    final Map<String, String> params = captor.getValue().getClientParams();

    assertThat(params.get(InstanceSyncConstants.HARNESS_ACCOUNT_ID)).isEqualTo(ACCOUNT_ID);
    assertThat(params.get(InstanceSyncConstants.HARNESS_APPLICATION_ID)).isEqualTo(APP_ID);
    assertThat(params.get(InstanceSyncConstants.HARNESS_ENV_ID)).isEqualTo(ENV_ID);
    assertThat(params.get(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID)).isEqualTo(INFRA_MAPPING_ID);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void shouldResetTaskOnNewDeployment() {
    final List<String> taskIds =
        perpetualTaskCreator.createPerpetualTasksForNewDeployment(Arrays.asList(DeploymentSummary.builder().build()),
            singletonList(PerpetualTaskRecord.builder().accountId(ACCOUNT_ID).uuid(UUID).build()), infraMapping);

    assertThat(taskIds).isEmpty();

    verify(perpetualTaskService, never()).createTask(any(), anyString(), any(), any(), anyBoolean(), anyString());

    verify(perpetualTaskService, times(1)).resetTask(ACCOUNT_ID, UUID, null);
  }

  private InfrastructureMapping buildInfraMapping() {
    CustomInfrastructureMapping infrastructureMapping = CustomInfrastructureMapping.builder().build();
    infrastructureMapping.setDeploymentTypeTemplateVersion("1");
    infrastructureMapping.setCustomDeploymentTemplateId(TEMPLATE_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    infrastructureMapping.setAccountId(ACCOUNT_ID);
    infrastructureMapping.setAppId(APP_ID);
    infrastructureMapping.setEnvId(ENV_ID);
    infrastructureMapping.setServiceId(SERVICE_ID);
    infrastructureMapping.setInfraMappingType(InfrastructureMappingType.CUSTOM.name());
    infrastructureMapping.setName(INFRA_MAPPING_NAME);
    infrastructureMapping.setDisplayName("infraName");
    return infrastructureMapping;
  }
}
