/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet;

import static io.harness.rule.OwnerRule.HITESH;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.cloudevents.aws.ecs.service.intfc.AwsECSClusterService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsEC2HelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsECSHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.EcsMetricClient;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.EcsUtilizationData;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.MetricValue;
import io.harness.batch.processing.dao.intfc.ECSServiceDao;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingValue;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.Attribute;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;

@RunWith(MockitoJUnitRunner.class)
public class AwsECSClusterDataSyncTaskletTest extends CategoryTest {
  private static final String accountId = "accountId";
  private static final String settingId = "settingId";
  private static final Instant instant = Instant.now().truncatedTo(ChronoUnit.HOURS);
  private static final long startTime = instant.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private static final long endTime = instant.toEpochMilli();
  private final String REGION = "us-east-1";
  private final String ACCOUNT_ID = "accountId";
  private final String INFRA_ACCOUNT_ID = "infraAccountId";
  private final String SETTING_ID = "settingId";
  private final String CLUSTER_NAME = "ecs-ccm-cluster";
  private final String CLUSTER_ARN = "arn:aws:ecs:us-east-2:132359207506:cluster/ecs-ccm-cluster";
  private final String AWS_ACCOUNT_ID = "awsAccountId";
  private final String AWS_ACCOUNT_NAME = "awsAccountName";
  private final String AWS_MASTER_ACCOUNT_ID = "awsMasterAccountId";
  private final String EXTERNAL_ID = "EXTERNAL_ID" + this.getClass().getSimpleName();
  private final String ROLE_ARN = "ROLE_ARN" + this.getClass().getSimpleName();
  private final String CONTAINER_INSTANCE_ARN = "arn:aws:ecs:us-east-1:132359207506:cluster/ce-ecs-ec2-test";
  private final String TASK_ARN = "arn:aws:ecs:us-east-1:132359207506:cluster/0fcd0a44-b82f-4f23-84ee-3ad8b40625a2";
  private final String SERVICE_ARN = "arn:aws:ecs:us-east-1:132359207506:service/ce-fargate";
  private final String SERVICE_NAME = "ce-fargate";
  private final String DEPLOYMENT_ID = "ecs-svc/7379042797953014107";
  private final String EC2_INSTANCE_ID = "i-0f0afe3d9df9b095c";
  @Mock protected InstanceDataService instanceDataService;
  @Mock protected InstanceResourceService instanceResourceService;
  @Spy @InjectMocks private AwsECSClusterDataSyncTasklet awsECSClusterDataSyncTasklet;
  @Mock private CEClusterDao ceClusterDao;
  @Mock private EcsMetricClient ecsMetricClient;
  @Mock private InstanceDataDao instanceDataDao;
  @Mock private CECloudAccountDao ceCloudAccountDao;
  @Mock private ECSServiceDao ecsServiceDao;
  @Mock private AwsECSHelperService awsECSHelperService;
  @Mock private AwsEC2HelperService awsEC2HelperService;
  @Mock private UtilizationDataServiceImpl utilizationDataService;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private AwsECSClusterService awsECSClusterService;
  @Mock private NGConnectorHelper ngConnectorHelper;
  @Mock private LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;

  private MetricValue getMetricValue(String metricName, String statistic, long startTime, double value) {
    return MetricValue.builder()
        .metricName(metricName)
        .statistic(statistic)
        .timestamps(singletonList(Date.from(Instant.ofEpochMilli(startTime))))
        .values(singletonList(value))
        .build();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldQueryEcsMetricClientAndPublishUtilizationMessages() throws Exception {
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
    final ImmutableList<Service> services =
        ImmutableList.of(new Service()
                             .withServiceArn("arn:aws:ecs:us-east-2:132359207506:service/ccm-test-service")
                             .withServiceName("ccm-test-service"),
            new Service()
                .withServiceArn("arn:aws:ecs:us-east-1:132359207506:service/ccm-test-service")
                .withServiceName("ccm-test-service"));
    List<EcsUtilizationData> utilizationMessages =
        ImmutableList.of(EcsUtilizationData.builder()
                             .clusterArn("cluster1-arn")
                             .clusterName("clusterName")
                             .clusterId("BNirC4Mg48Vf2n7LAznpPgqGggk=")
                             .settingId(settingId)
                             .metricValues(Arrays.asList(getMetricValue("CPUUtilization", "Average", startTime, 78.3),
                                 getMetricValue("CPUUtilization", "Maximum", startTime, 78.3),
                                 getMetricValue("MemoryUtilization", "Average", startTime, 78.3),
                                 getMetricValue("MemoryUtilization", "Maximum", startTime, 78.3)))
                             .build(),

            EcsUtilizationData.builder()
                .clusterArn("cluster1-arn")
                .clusterName("clusterName")
                .serviceArn("svc2-arn")
                .serviceName("svc2")
                .clusterId("BNirC4Mg48Vf2n7LAznpPgqGggk=")
                .settingId(settingId)
                .metricValues(Arrays.asList(getMetricValue("CPUUtilization", "Average", endTime, 66.6),
                    getMetricValue("CPUUtilization", "Maximum", endTime, 66.6),
                    getMetricValue("MemoryUtilization", "Average", endTime, 66.6),
                    getMetricValue("MemoryUtilization", "Maximum", endTime, 66.6)))
                .build());

    AwsCrossAccountAttributes awsCrossAccountAttributes = AwsCrossAccountAttributes.builder().build();
    CECluster ceCluster = CECluster.builder()
                              .accountId(ACCOUNT_ID)
                              .region(REGION)
                              .infraAccountId(INFRA_ACCOUNT_ID)
                              .clusterName(CLUSTER_NAME)
                              .clusterName(CLUSTER_ARN)
                              .build();
    given(ecsMetricClient.getUtilizationMetrics(any(), any(), any(), any(), any(), any()))
        .willReturn(utilizationMessages);

    awsECSClusterDataSyncTasklet.publishUtilizationMetrics(awsCrossAccountAttributes, ceCluster, services);

    ArgumentCaptor<List<InstanceUtilizationData>> utilizationDataArgumentCaptor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(utilizationDataService).create(utilizationDataArgumentCaptor.capture());
    List<List<InstanceUtilizationData>> allValues = utilizationDataArgumentCaptor.getAllValues();
    assertThat(allValues.get(0)).hasSize(2);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldSyncCluster() throws Exception {
    ChunkContext chunkContext = mock(ChunkContext.class);
    StepContext stepContext = mock(StepContext.class);
    StepExecution stepExecution = mock(StepExecution.class);
    JobParameters parameters = mock(JobParameters.class);
    when(chunkContext.getStepContext()).thenReturn(stepContext);
    when(stepContext.getStepExecution()).thenReturn(stepExecution);
    when(stepExecution.getJobParameters()).thenReturn(parameters);
    AwsCrossAccountAttributes awsCrossAccountAttributes = getAwsCrossAccountAttributes();

    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(startTime));
    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(endTime));
    when(ceClusterDao.getCECluster(ACCOUNT_ID))
        .thenReturn(ImmutableList.of(getCeCluster(), getCeCluster(), getCeCluster()));
    when(ceCloudAccountDao.getByAWSAccountId(ACCOUNT_ID))
        .thenReturn(Collections.singletonList(CECloudAccount.builder()
                                                  .infraAccountId(AWS_ACCOUNT_ID)
                                                  .awsCrossAccountAttributes(awsCrossAccountAttributes)
                                                  .accountName(AWS_ACCOUNT_NAME)
                                                  .build()));

    SettingValue settingValue = CEAwsConfig.builder().awsCrossAccountAttributes(awsCrossAccountAttributes).build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withCategory(SettingAttribute.SettingCategory.CE_CONNECTOR)
                                            .withValue(settingValue)
                                            .build();
    Mockito.doReturn(Arrays.asList(settingAttribute))
        .when(cloudToHarnessMappingService)
        .listSettingAttributesCreatedInDuration(any(), any(), any());

    Mockito.doReturn(emptyList()).when(ngConnectorHelper).getNextGenConnectors(any(), any(), any(), any());

    RepeatStatus execute = awsECSClusterDataSyncTasklet.execute(null, chunkContext);
    assertThat(execute).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testUpdateContainerInstance() {
    HashMap<String, Instance> instanceMap = new HashMap<>();
    instanceMap.put(EC2_INSTANCE_ID, getInstance());

    CECluster ceCluster = getCeCluster();
    AwsCrossAccountAttributes awsCrossAccountAttributes = getAwsCrossAccountAttributes();
    when(awsECSClusterDataSyncTasklet.listEc2Instances(any(), any(), any())).thenReturn(instanceMap);
    when(instanceResourceService.getComputeVMResource(any(), any(), any()))
        .thenReturn(io.harness.ccm.commons.beans.Resource.builder().cpuUnits(1024.0).memoryMb(1024.0).build());
    awsECSClusterDataSyncTasklet.updateContainerInstance(
        ACCOUNT_ID, ceCluster, awsCrossAccountAttributes, Arrays.asList(getContainerInstance()));

    ArgumentCaptor<InstanceData> captor = ArgumentCaptor.forClass(InstanceData.class);
    then(instanceDataService).should().create(captor.capture());
    InstanceData instanceData = captor.getValue();
    assertThat(instanceData.getInstanceId()).isEqualTo("ce-ecs-ec2-test");
    assertThat(instanceData.getInstanceType()).isEqualTo(InstanceType.ECS_CONTAINER_INSTANCE);
    assertThat(instanceData.getAllocatableResource())
        .isEqualTo(io.harness.ccm.commons.beans.Resource.builder().cpuUnits(1.0).memoryMb(1024.0).build());
    assertThat(instanceData.getUsageStartTime()).isEqualTo(instant);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testUpdateTasks() {
    HashMap<String, String> deploymentIdServiceMap = new HashMap<>();
    deploymentIdServiceMap.put(DEPLOYMENT_ID, SERVICE_ARN);
    CECluster ceCluster = getCeCluster();
    when(cloudToHarnessMappingService.getHarnessServiceInfo(any()))
        .thenReturn(Optional.of(new HarnessServiceInfo(null, null, null, null, null, null)));
    when(instanceDataDao.fetchInstanceData(anyString(), anySet())).thenReturn(singletonList(getInstanceData()));
    awsECSClusterDataSyncTasklet.updateTasks(accountId, ceCluster, singletonList(getTask()), deploymentIdServiceMap,
        Collections.EMPTY_MAP, Instant.ofEpochMilli(startTime));
    ArgumentCaptor<InstanceData> captor = ArgumentCaptor.forClass(InstanceData.class);
    then(instanceDataService).should().create(captor.capture());
    InstanceData instanceData = captor.getValue();
    Map<String, String> instanceDataMetaData = instanceData.getMetaData();
    assertThat(instanceData.getInstanceId()).isEqualTo("0fcd0a44-b82f-4f23-84ee-3ad8b40625a2");
    assertThat(instanceDataMetaData.get(InstanceMetaDataConstants.ECS_SERVICE_ARN)).isEqualTo(SERVICE_ARN);
    assertThat(instanceData.getInstanceType()).isEqualTo(InstanceType.ECS_TASK_EC2);
    assertThat(instanceData.getAllocatableResource())
        .isEqualTo(io.harness.ccm.commons.beans.Resource.builder().cpuUnits(1.0).memoryMb(512.0).build());
    assertThat(instanceData.getUsageStartTime()).isEqualTo(instant);
    assertThat(instanceDataMetaData.get(InstanceMetaDataConstants.INSTANCE_FAMILY))
        .isEqualTo(InstanceMetaDataConstants.INSTANCE_FAMILY);
  }

  private InstanceData getInstanceData() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceMetaDataConstants.INSTANCE_CATEGORY);
    metaData.put(InstanceMetaDataConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    metaData.put(InstanceMetaDataConstants.CONTAINER_INSTANCE_ARN, InstanceMetaDataConstants.CONTAINER_INSTANCE_ARN);
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, InstanceMetaDataConstants.PARENT_RESOURCE_ID);
    metaData.put(
        InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID);
    return InstanceData.builder()
        .instanceId("ce-ecs-ec2-test")
        .metaData(metaData)
        .totalResource(io.harness.ccm.commons.beans.Resource.builder().cpuUnits(1024.0).memoryMb(1024.0).build())
        .build();
  }

  private Task getTask() {
    return new Task()
        .withTaskArn(TASK_ARN)
        .withContainerInstanceArn(CONTAINER_INSTANCE_ARN)
        .withClusterArn(CLUSTER_ARN)
        .withLaunchType(LaunchType.EC2)
        .withMemory("512")
        .withCpu("1")
        .withDesiredStatus(DesiredStatus.RUNNING.toString())
        .withStartedBy(DEPLOYMENT_ID)
        .withPullStartedAt(Date.from(instant));
  }

  private Instance getInstance() {
    return new Instance().withInstanceId(EC2_INSTANCE_ID).withInstanceType("t2.micro");
  }

  private CECluster getCeCluster() {
    return CECluster.builder()
        .accountId(ACCOUNT_ID)
        .clusterName("clusterName1")
        .clusterArn(CLUSTER_ARN)
        .region("us-east-1")
        .infraAccountId(AWS_ACCOUNT_ID)
        .infraMasterAccountId(AWS_MASTER_ACCOUNT_ID)
        .parentAccountSettingId(SETTING_ID)
        .build();
  }

  private ContainerInstance getContainerInstance() {
    return new ContainerInstance()
        .withContainerInstanceArn(CONTAINER_INSTANCE_ARN)
        .withEc2InstanceId(EC2_INSTANCE_ID)
        .withRegisteredAt(Date.from(instant))
        .withStatus(ContainerInstanceStatus.ACTIVE.toString())
        .withRegisteredResources(Arrays.asList(getResource("CPU", 1), getResource("MEMORY", 1024)))
        .withAttributes(getAttribute("ecs.os-type", "linux"));
  }

  private Resource getResource(String name, int val) {
    return new Resource().withName(name).withIntegerValue(val);
  }

  private Attribute getAttribute(String attName, String attValue) {
    return new Attribute().withName(attName).withValue(attValue);
  }

  private AwsCrossAccountAttributes getAwsCrossAccountAttributes() {
    return AwsCrossAccountAttributes.builder().externalId(EXTERNAL_ID).crossAccountRoleArn(ROLE_ARN).build();
  }
}
