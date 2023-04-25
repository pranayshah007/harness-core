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
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.AwsAmiInstanceSyncPerpetualTaskClient;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.api.AwsAutoScalingGroupDeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.AutoScalingGroupInstanceInfo;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.InstanceService;

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
public class AwsAmiInstanceSyncPerpetualTaskCreatorTest extends CategoryTest {
  @Mock private InstanceService instanceService;
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;

  @InjectMocks private AwsAmiInstanceSyncPerpetualTaskCreator perpetualTaskCreator;

  public static final String ASG_NAME = "asgName";
  public static final PerpetualTaskSchedule SCHEDULE = PerpetualTaskSchedule.newBuilder()
                                                           .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                                           .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                                           .build();
  private AwsAmiInfrastructureMapping infrastructureMapping;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    infrastructureMapping = getAmiInfraMapping();
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment().accountId(ACCOUNT_ID).appId(APP_ID).name(ENV_NAME).build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder().accountId(ACCOUNT_ID).appId(APP_ID).name(SERVICE_NAME).build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication().appId(APP_ID).accountId(ACCOUNT_ID).name(APP_NAME).build());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void createPerpetualTasks() {
    doReturn(getAsgInstances()).when(instanceService).getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.AWS_AMI_INSTANCE_SYNC), eq(ACCOUNT_ID), any(), any(), eq(false), eq(""));

    perpetualTaskCreator.createPerpetualTasks(infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(3))
        .createTask(eq(PerpetualTaskType.AWS_AMI_INSTANCE_SYNC), eq(ACCOUNT_ID), captor.capture(), eq(SCHEDULE),
            eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(ASG_NAME)))
        .containsExactlyInAnyOrder("asg-1", "asg-2", "asg-3");
  }

  private AwsAmiInfrastructureMapping getAmiInfraMapping() {
    AwsAmiInfrastructureMapping infraMapping = new AwsAmiInfrastructureMapping();

    infraMapping.setAppId(APP_ID);
    infraMapping.setAccountId(ACCOUNT_ID);
    infraMapping.setUuid(INFRA_MAPPING_ID);
    infraMapping.setDisplayName("infraName");
    infraMapping.setName(INFRA_MAPPING_NAME);
    return infraMapping;
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void createPerpetualTasksForNewDeployment() {
    List<PerpetualTaskRecord> existingRecords = asList(
        PerpetualTaskRecord.builder()
            .clientContext(PerpetualTaskClientContext.builder()
                               .clientParams(ImmutableMap.of(AwsAmiInstanceSyncPerpetualTaskClient.ASG_NAME, "asg-1"))
                               .build())
            .build());

    perpetualTaskCreator.createPerpetualTasksForNewDeployment(
        asList(DeploymentSummary.builder()
                   .appId(APP_ID)
                   .accountId(ACCOUNT_ID)
                   .infraMappingId(INFRA_MAPPING_ID)
                   .deploymentInfo(AwsAutoScalingGroupDeploymentInfo.builder().autoScalingGroupName("asg-1").build())
                   .build(),
            DeploymentSummary.builder()
                .appId(APP_ID)
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .deploymentInfo(AwsAutoScalingGroupDeploymentInfo.builder().autoScalingGroupName("asg-2").build())
                .build()),
        existingRecords, infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.AWS_AMI_INSTANCE_SYNC), eq(ACCOUNT_ID), captor.capture(), eq(SCHEDULE),
            eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(ASG_NAME)))
        .containsExactlyInAnyOrder("asg-2");
  }

  private List<Instance> getAsgInstances() {
    return asList(Instance.builder()
                      .uuid("id-1")
                      .instanceInfo(AutoScalingGroupInstanceInfo.builder().autoScalingGroupName("asg-1").build())
                      .build(),
        Instance.builder()
            .uuid("id-2")
            .instanceInfo(AutoScalingGroupInstanceInfo.builder().autoScalingGroupName("asg-2").build())
            .build(),
        Instance.builder()
            .uuid("id-3")
            .instanceInfo(AutoScalingGroupInstanceInfo.builder().autoScalingGroupName("asg-3").build())
            .build(),
        Instance.builder().uuid("id-4").instanceInfo(Ec2InstanceInfo.builder().hostName("ip-1").build()).build(),
        Instance.builder()
            .uuid("id-5")
            .instanceInfo(AutoScalingGroupInstanceInfo.builder().autoScalingGroupName("asg-1").build())
            .build());
  }
}
