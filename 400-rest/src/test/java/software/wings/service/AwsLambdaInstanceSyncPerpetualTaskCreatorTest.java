/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskClient.START_DATE;

import static software.wings.service.InstanceSyncConstants.INTERVAL_MINUTES;
import static software.wings.service.InstanceSyncConstants.TIMEOUT_SECONDS;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.api.DeploymentSummary;
import software.wings.api.lambda.AwsLambdaDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.infrastructure.instance.key.AwsLambdaInstanceKey;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.ServerlessInstanceService;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.util.Durations;
import java.util.Date;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class AwsLambdaInstanceSyncPerpetualTaskCreatorTest extends CategoryTest {
  @Mock ServerlessInstanceService serverlessInstanceService;
  @Mock PerpetualTaskService perpetualTaskService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;
  public static final String FUNCTION_NAME = "functionName";
  public static final String QUALIFIER = "qualifier";
  public static final PerpetualTaskSchedule SCHEDULE = PerpetualTaskSchedule.newBuilder()
                                                           .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                                           .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                                           .build();

  @InjectMocks private AwsLambdaInstanceSyncPerpetualTaskCreator perpetualTaskCreator;
  private AwsLambdaInfraStructureMapping infraStructureMapping;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    infraStructureMapping = AwsLambdaInfraStructureMapping.builder()
                                .uuid(INFRA_MAPPING_ID)
                                .accountId(ACCOUNT_ID)
                                .name(INFRA_MAPPING_NAME)
                                .appId(APP_ID)
                                .build();
    infraStructureMapping.setDisplayName("infraName");
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment().accountId(ACCOUNT_ID).appId(APP_ID).name(ENV_NAME).build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder().accountId(ACCOUNT_ID).appId(APP_ID).name(SERVICE_NAME).build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication().appId(APP_ID).accountId(ACCOUNT_ID).name(APP_NAME).build());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void createPerpetualTasks() {
    doReturn(getServerlessInstances()).when(serverlessInstanceService).list(anyString(), anyString());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID), any(),
            any(), eq(false), eq(""));

    perpetualTaskCreator.createPerpetualTasks(infraStructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(3))
        .createTask(eq(PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            captor.capture(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(FUNCTION_NAME)))
        .containsExactlyInAnyOrder("function-1", "function-2", "function-3");

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(QUALIFIER)))
        .containsExactlyInAnyOrder("version-1", "version-2", "version-3");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void createPerpetualTasksForNewDeployment() {
    Date now = new Date();
    List<PerpetualTaskRecord> existingRecords =
        asList(PerpetualTaskRecord.builder()
                   .clientContext(PerpetualTaskClientContext.builder()
                                      .clientParams(ImmutableMap.of(FUNCTION_NAME, "function-1", QUALIFIER, "version-1",
                                          START_DATE, String.valueOf(now.getTime())))
                                      .build())
                   .build());

    perpetualTaskCreator.createPerpetualTasksForNewDeployment(
        asList(DeploymentSummary.builder()
                   .appId(InstanceSyncTestConstants.APP_ID)
                   .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                   .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                   .deployedAt(now.getTime())
                   .deploymentInfo(
                       AwsLambdaDeploymentInfo.builder().functionName("function-1").version("version-1").build())
                   .build(),
            DeploymentSummary.builder()
                .appId(InstanceSyncTestConstants.APP_ID)
                .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .deployedAt(now.getTime())
                .deploymentInfo(
                    AwsLambdaDeploymentInfo.builder().functionName("function-2").version("version-2").build())
                .build()),
        existingRecords, infraStructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            captor.capture(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(FUNCTION_NAME)))
        .containsExactlyInAnyOrder("function-2");
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(QUALIFIER)))
        .containsExactlyInAnyOrder("version-2");
  }

  private List<ServerlessInstance> getServerlessInstances() {
    return asList(
        ServerlessInstance.builder()
            .uuid("id-1")
            .lambdaInstanceKey(
                AwsLambdaInstanceKey.builder().functionName("function-1").functionVersion("version-1").build())
            .build(),
        ServerlessInstance.builder()
            .uuid("id-2")
            .lambdaInstanceKey(
                AwsLambdaInstanceKey.builder().functionName("function-2").functionVersion("version-2").build())
            .build(),
        ServerlessInstance.builder()
            .uuid("id-3")
            .lambdaInstanceKey(
                AwsLambdaInstanceKey.builder().functionName("function-3").functionVersion("version-3").build())
            .build(),
        ServerlessInstance.builder()
            .uuid("id-4")
            .lambdaInstanceKey(
                AwsLambdaInstanceKey.builder().functionName("function-1").functionVersion("version-1").build())
            .build());
  }
}
