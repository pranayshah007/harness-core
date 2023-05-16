/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static software.wings.beans.CodeDeployInfrastructureMapping.CodeDeployInfrastructureMappingBuilder.aCodeDeployInfrastructureMapping;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INTERVAL_MINUTES;
import static software.wings.service.InstanceSyncConstants.TIMEOUT_SECONDS;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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

import software.wings.api.DeploymentSummary;
import software.wings.beans.Application;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.util.Durations;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsCodeDeployInstanceSyncPerpetualTaskCreatorTest extends CategoryTest {
  public static final PerpetualTaskSchedule SCHEDULE = PerpetualTaskSchedule.newBuilder()
                                                           .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                                           .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                                           .build();
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @InjectMocks AwsCodeDeployInstanceSyncPerpetualTaskCreator taskCreator;
  @Mock private ServiceResourceService serviceResourceService;
  private CodeDeployInfrastructureMapping infrastructureMapping;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn("task-id")
        .when(perpetualTaskService)
        .createTask(
            eq(PerpetualTaskType.AWS_CODE_DEPLOY_INSTANCE_SYNC), anyString(), any(), any(), eq(false), anyString());
    infrastructureMapping = getInfrastructureMapping();
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment().accountId(ACCOUNT_ID).appId(APP_ID).name(ENV_NAME).build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder().accountId(ACCOUNT_ID).appId(APP_ID).name(SERVICE_NAME).build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication().appId(APP_ID).accountId(ACCOUNT_ID).name(APP_NAME).build());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTasks() {
    List<String> taskIds = taskCreator.createPerpetualTasks(infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.AWS_CODE_DEPLOY_INSTANCE_SYNC), eq(ACCOUNT_ID), captor.capture(), eq(SCHEDULE),
            eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(taskIds).containsExactlyInAnyOrder("task-id");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTasksForNewDeployment() {
    List<DeploymentSummary> deploymentSummaries = singletonList(
        DeploymentSummary.builder().accountId(ACCOUNT_ID).appId(APP_ID).infraMappingId(INFRA_MAPPING_ID).build());
    List<PerpetualTaskRecord> existingTasks = emptyList();

    List<String> taskIds =
        taskCreator.createPerpetualTasksForNewDeployment(deploymentSummaries, existingTasks, infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.AWS_CODE_DEPLOY_INSTANCE_SYNC), eq(ACCOUNT_ID), captor.capture(), eq(SCHEDULE),
            eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(taskIds).containsExactlyInAnyOrder("task-id");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTasksForNewDeploymentsWhenTaskExistsForAppId() {
    List<DeploymentSummary> deploymentSummaries = singletonList(
        DeploymentSummary.builder().accountId(ACCOUNT_ID).appId(APP_ID).infraMappingId(INFRA_MAPPING_ID).build());
    List<PerpetualTaskRecord> existingTasks =
        singletonList(PerpetualTaskRecord.builder()
                          .clientContext(PerpetualTaskClientContext.builder()
                                             .clientParams(ImmutableMap.of(HARNESS_APPLICATION_ID, APP_ID))
                                             .build())
                          .build());

    List<String> taskIds =
        taskCreator.createPerpetualTasksForNewDeployment(deploymentSummaries, existingTasks, infrastructureMapping);

    verify(perpetualTaskService, never())
        .createTask(eq(PerpetualTaskType.AWS_CODE_DEPLOY_INSTANCE_SYNC), eq(ACCOUNT_ID), any(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(taskIds).isEmpty();
  }

  private CodeDeployInfrastructureMapping getInfrastructureMapping() {
    CodeDeployInfrastructureMapping infrastructureMapping = aCodeDeployInfrastructureMapping()
                                                                .withAccountId(ACCOUNT_ID)
                                                                .withAppId(APP_ID)
                                                                .withUuid(INFRA_MAPPING_ID)
                                                                .withName(INFRA_MAPPING_NAME)
                                                                .build();
    infrastructureMapping.setDisplayName("infraName");
    return infrastructureMapping;
  }
}
