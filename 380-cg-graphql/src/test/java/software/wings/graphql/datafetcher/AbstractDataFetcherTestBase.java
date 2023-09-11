/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static software.wings.beans.Account.Builder.anAccount;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.EcsCluster;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.ccm.setup.CEClusterDao;
import io.harness.persistence.HPersistence;

import software.wings.WingsBaseTest;
import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.Builder;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.LicenseInfo;
import software.wings.beans.PhysicalDataCenterConfig;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.dl.WingsPersistence;
import software.wings.events.TestUtils;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(DX)
public abstract class AbstractDataFetcherTestBase extends WingsBaseTest {
  public static final String TAG_TEAM = "TEAM";
  public static final String TAG_VALUE_TEAM1 = "TEAM1";
  public static final String TAG_VALUE_TEAM2 = "TEAM2";
  public static final String TAG_TEAM1 = "TEAM:TEAM1";
  public static final String TAG_TEAM2 = "TEAM:TEAM2";
  public static final String TAG_MODULE = "MODULE";
  public static final String TAG_VALUE_MODULE1 = "MODULE1";
  public static final String TAG_VALUE_MODULE2 = "MODULE2";
  public static final String TAG_MODULE1 = "MODULE:MODULE1";
  public static final String TAG_MODULE2 = "MODULE:MODULE2";
  public static final String INFRA1_ID_ENV1_APP1_ACCOUNT1 = "INFRA1_ID_ENV1_APP1_ACCOUNT1";
  public static final String INFRA2_ID_ENV1_APP1_ACCOUNT1 = "INFRA2_ID_ENV1_APP1_ACCOUNT1";
  public static final String TAG_ENVTYPE = "ENVTYPE";
  public static final String TAG_VALUE_PROD = "PROD";
  public static final String TAG_VALUE_NON_PROD = "NON_PROD";
  public static final String TAG_PROD = "ENVTYPE:PROD";
  public static final String TAG_NON_PROD = "ENVTYPE:NON_PROD";
  public static final String ACCOUNT1_ID = "ACCOUNT1_ID";
  public static final String APP1_ID_ACCOUNT1 = "APP1_ID_ACCOUNT1";
  public static final String SERVICE1_ID_APP1_ACCOUNT1 = "SERVICE1_ID_APP1_ACCOUNT1";
  public static final String SERVICE2_ID_APP1_ACCOUNT1 = "SERVICE2_ID_APP1_ACCOUNT1";
  public static final String ENV1_ID_APP1_ACCOUNT1 = "ENV1_ID_APP1_ACCOUNT1";
  public static final String ENV2_ID_APP1_ACCOUNT1 = "ENV2_ID_APP1_ACCOUNT1";
  public static final String INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1 = "INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1";
  public static final String INSTANCE2_SERVICE1_ENV1_APP1_ACCOUNT1 = "INSTANCE2_SERVICE1_ENV1_APP1_ACCOUNT1";
  public static final String INSTANCE3_SERVICE1_ENV2_APP1_ACCOUNT1 = "INSTANCE3_SERVICE1_ENV2_APP1_ACCOUNT1";
  public static final String INSTANCE4_SERVICE2_ENV2_APP1_ACCOUNT1 = "INSTANCE4_SERVICE2_ENV2_APP1_ACCOUNT1";
  public static final String APP2_ID_ACCOUNT1 = "APP2_ID_ACCOUNT1";
  public static final String SERVICE3_ID_APP2_ACCOUNT1 = "SERVICE3_ID_APP2_ACCOUNT1";
  public static final String ENV3_ID_APP2_ACCOUNT1 = "ENV3_ID_APP2_ACCOUNT1";
  public static final String ENV4_ID_APP2_ACCOUNT1 = "ENV4_ID_APP2_ACCOUNT1";
  public static final String INSTANCE5_SERVICE3_ENV3_APP2_ACCOUNT1 = "INSTANCE5_SERVICE3_ENV3_APP2_ACCOUNT1";
  public static final String INSTANCE6_SERVICE3_ENV4_APP2_ACCOUNT1 = "INSTANCE6_SERVICE3_ENV4_APP2_ACCOUNT1";
  public static final String CONNECTOR_ID1_ACCOUNT1 = "CONNECTOR1_ID_ACCOUNT1";
  public static final String CONNECTOR_ID2_ACCOUNT1 = "CONNECTOR2_ID_ACCOUNT1";
  public static final String CONNECTOR_ID3_ACCOUNT2 = "CONNECTOR3_ID_ACCOUNT2";
  public static final String CLOUD_PROVIDER1_ID_ACCOUNT1 = "CLOUD_PROVIDER1_ID_ACCOUNT1";
  public static final String CLOUD_PROVIDER2_ID_ACCOUNT1 = "CLOUD_PROVIDER2_ID_ACCOUNT1";
  public static final String CLOUD_PROVIDER3_ID_ACCOUNT2 = "CLOUD_PROVIDER3_ID_ACCOUNT2";
  public static final String CLOUD_SERVICE_NAME_ACCOUNT1 = "CLOUD_SERVICE_NAME_ACCOUNT1";
  public static final String WORKLOAD_NAME_ACCOUNT1 = "WORKLOAD_NAME_ACCOUNT1";
  public static final String WORKLOAD_TYPE_ACCOUNT1 = "WORKLOAD_TYPE_ACCOUNT1";
  public static final String TRIGGER_ID1_APP1_ACCOUNT1 = "TRIGGER_ID1_APP1_ACCOUNT1";
  public static final String TRIGGER_ID2_APP1_ACCOUNT1 = "TRIGGER_ID2_APP1_ACCOUNT1";
  public static final String TAG1_ID_ACCOUNT1 = "TAG1_ID_ACCOUNT1";
  public static final String TAG2_ID_ACCOUNT1 = "TAG2_ID_ACCOUNT1";
  public static final String TAG_KEY = "TAG_KEY";
  public static final String TAG_VALUE = "TAG_VALUE";
  public static final String ACCOUNT2_ID = "ACCOUNT2_ID";
  public static final String APP3_ID_ACCOUNT2 = "APP3_ID_ACCOUNT2";
  public static final String SERVICE4_ID_APP3_ACCOUNT2 = "SERVICE4_ID_APP3_ACCOUNT2";
  public static final String ENV5_ID_APP3_ACCOUNT2 = "ENV5_ID_APP3_ACCOUNT2";
  public static final String ENV6_ID_APP3_ACCOUNT2 = "ENV6_ID_APP3_ACCOUNT2";
  public static final String INSTANCE7_SERVICE4_ENV5_APP3_ACCOUNT2 = "INSTANCE7_SERVICE4_ENV5_APP3_ACCOUNT2";
  public static final String INSTANCE8_SERVICE4_ENV6_APP3_ACCOUNT2 = "INSTANCE8_SERVICE4_ENV6_APP3_ACCOUNT2";
  public static final String WORKFLOW1 = "WORKFLOW1";
  public static final String PIPELINE1 = "PIPELINE1";
  public static final String REGION1 = "REGION1";
  public static final String CLUSTER1_ID = "CLUSTER1_ID";
  public static final String CLUSTER2_ID = "5e144cbfececcf83f5b29cd5";
  public static final String CLUSTER1_NAME = "CLUSTER1_NAME";
  public static final String LAUNCH_TYPE1 = "LAUNCH_TYPE1";
  public static final String CLUSTER_TYPE1 = "CLUSTER_TYPE1";
  public static final String NAMESPACE1 = "NAMESPACE1";
  public static final String TASK1 = "TASK1";
  public static final String NODE1 = "NODE1";
  public static final String INSTANCE_TYPE1 = "INSTANCE_TYPE1";
  public static final String LABEL_NAME = "LABEL_NAME";
  public static final String LABEL_VALUE = "LABEL_VALUE";
  public static final String LABEL = "LABEL_VALUE";
  public static final String SETTING_ID1 = "SETTING_ID1";
  public static final String ARTIFACT_STREAM_ID_1 = "ARTIFACT_STREAM_ID_1";
  public static final String ARTIFACT_STREAM_ID_2 = "ARTIFACT_STREAM_ID_2";
  public static final String SETTING_ID_1 = "SETTING_ID_1";
  public static final String SETTING_ID_2 = "SETTING_ID_2";
  @Inject protected AccountService accountService;
  @Inject protected AppService appService;
  @Inject protected HarnessTagService harnessTagService;
  @Inject protected ServiceResourceService serviceResourceService;
  @Inject protected EnvironmentService environmentService;
  @Inject protected InstanceService instanceService;
  @Inject protected InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject protected SettingsService settingsService;
  @Inject protected TriggerService triggerService;
  @Inject protected WorkflowService workflowService;
  @Inject protected PipelineService pipelineService;
  @Inject protected WorkflowExecutionService workflowExecutionService;
  @Inject protected ClusterRecordService clusterRecordService;
  @Inject protected CEClusterDao clusterDao;
  @Inject protected CECloudAccountDao cloudAccountDao;
  @Inject protected HPersistence hPersistence;
  @Inject protected TestUtils testUtils;
  @Inject private WingsPersistence wingsPersistence;

  public Account createAccount(String accountId, LicenseInfo licenseInfo) {
    return accountService.save(anAccount()
                                   .withCompanyName(accountId)
                                   .withAccountName(accountId)
                                   .withAccountKey("ACCOUNT_KEY")
                                   .withLicenseInfo(licenseInfo)
                                   .withUuid(accountId)
                                   .build(),
        false);
  }

  public Application createApp(String accountId, String appId, String appName, String tagKey, String tagValue) {
    Application application =
        appService.save(Builder.anApplication().name(appName).accountId(accountId).uuid(appId).build());
    setTagToEntity(tagKey, tagValue, accountId, appId, appId, EntityType.APPLICATION);
    return application;
  }

  public String createWorkflowExecution(String accountId, String appId, String workflowId) {
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .workflowId(workflowId)
                                              .workflowType(WorkflowType.ORCHESTRATION)
                                              .status(ExecutionStatus.SUCCESS)
                                              .accountId(accountId)
                                              .appId(appId)
                                              .build();
    return wingsPersistence.insert(workflowExecution);
  }

  public String createWorkflowExecution(
      String accountId, String appId, String workflowId, EnvironmentType environmentType) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("buildNo", "1");
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .workflowId(workflowId)
            .workflowType(WorkflowType.ORCHESTRATION)
            .status(ExecutionStatus.SUCCESS)
            .accountId(accountId)
            .appId(appId)
            .envType(environmentType)
            .executionArgs(ExecutionArgs.builder()
                               .artifacts(List.of(
                                   Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(metadata)).build()))
                               .helmCharts(List.of(HelmChart.builder().displayName("helm-1.23").build()))
                               .build())
            .build();
    return wingsPersistence.insert(workflowExecution);
  }

  public String createPipelineExecution(String accountId, String appId, String pipelineId) {
    long startTs = System.currentTimeMillis();
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .workflowId(pipelineId)
                                              .workflowType(WorkflowType.PIPELINE)
                                              .status(ExecutionStatus.SUCCESS)
                                              .startTs(startTs)
                                              .endTs(startTs + 10)
                                              .duration(10L)
                                              .accountId(accountId)
                                              .appId(appId)
                                              .build();
    return wingsPersistence.insert(workflowExecution);
  }

  public String createPipelineExecution(
      String accountId, String appId, String pipelineId, EnvironmentType environmentType) {
    long startTs = System.currentTimeMillis();
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .workflowId(pipelineId)
                                              .workflowType(WorkflowType.PIPELINE)
                                              .status(ExecutionStatus.SUCCESS)
                                              .startTs(startTs)
                                              .endTs(startTs + 10)
                                              .duration(10L)
                                              .accountId(accountId)
                                              .appId(appId)
                                              .envType(environmentType)
                                              .build();
    return wingsPersistence.insert(workflowExecution);
  }

  public Workflow createWorkflow(String accountId, String appId, String workflowName) {
    return workflowService.createWorkflow(
        WorkflowBuilder.aWorkflow()
            .workflowType(WorkflowType.ORCHESTRATION)
            .accountId(accountId)
            .appId(appId)
            .name(workflowName)
            .orchestrationWorkflow(
                BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build())
            .build());
  }

  public Pipeline createPipeline(String accountId, String appId, String pipelineName) {
    return pipelineService.save(Pipeline.builder().accountId(accountId).appId(appId).name(pipelineName).build());
  }

  public void setTagToEntity(
      String tagKey, String tagValue, String accountId, String appId, String entityId, EntityType entityType) {
    harnessTagService.attachTagWithoutGitPush(HarnessTagLink.builder()
                                                  .key(tagKey)
                                                  .value(tagValue)
                                                  .entityId(entityId)
                                                  .entityType(entityType)
                                                  .accountId(accountId)
                                                  .appId(appId)
                                                  .build());
  }

  public Service createService(
      String accountId, String appId, String serviceId, String serviceName, String tagKey, String tagValue) {
    Service service = serviceResourceService.save(
        Service.builder().name(serviceName).uuid(serviceId).appId(appId).accountId(accountId).build());
    setTagToEntity(tagKey, tagValue, accountId, appId, serviceId, EntityType.SERVICE);
    return service;
  }

  public Service createService(String accountId, String appId, String serviceId, String serviceName, String tagKey,
      String tagValue, DeploymentType deploymentType) {
    Service service = serviceResourceService.save(Service.builder()
                                                      .name(serviceName)
                                                      .uuid(serviceId)
                                                      .appId(appId)
                                                      .accountId(accountId)
                                                      .deploymentType(deploymentType)
                                                      .build());
    setTagToEntity(tagKey, tagValue, accountId, appId, serviceId, EntityType.SERVICE);
    return service;
  }

  public InfrastructureDefinition createInfrastructureDefinition(
      String accountId, String envId, String appId, String infrastructureId, String infrastructureName) {
    InfraMappingInfrastructureProvider infraMappingInfrastructureProvider = AwsAmiInfrastructure.builder().build();
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionService.save(InfrastructureDefinition.builder()
                                                 .name(infrastructureName)
                                                 .uuid(infrastructureId)
                                                 .envId(envId)
                                                 .accountId(accountId)
                                                 .cloudProviderType(CloudProviderType.AWS)
                                                 .appId(appId)
                                                 .infrastructure(infraMappingInfrastructureProvider)
                                                 .deploymentType(DeploymentType.AMI)
                                                 .build(),
            true);
    return infrastructureDefinition;
  }

  public Environment createEnv(
      String accountId, String appId, String envId, String envName, String tagKey, String tagValue) {
    Environment environment = environmentService.save(
        Environment.Builder.anEnvironment().name(envName).uuid(envId).appId(appId).accountId(accountId).build());
    setTagToEntity(tagKey, tagValue, accountId, appId, envId, EntityType.ENVIRONMENT);
    return environment;
  }

  public Trigger createCustomTrigger(
      String accountId, String appId, String triggerId, String triggerName, String workflowId) {
    return triggerService.save(Trigger.builder()
                                   .name(triggerName)
                                   .uuid(triggerId)
                                   .appId(appId)
                                   .condition(WebHookTriggerCondition.builder().build())
                                   .workflowId(workflowId)
                                   .workflowType(WorkflowType.ORCHESTRATION)
                                   .accountId(accountId)
                                   .build());
  }

  public Instance createInstance(String accountId, String appId, String envId, String serviceId,
      EnvironmentType envType, String instanceId, String cloudProviderId) {
    return instanceService.save(Instance.builder()
                                    .accountId(accountId)
                                    .appId(appId)
                                    .appName(appId)
                                    .serviceId(serviceId)
                                    .serviceName(serviceId)
                                    .envId(envId)
                                    .envType(envType)
                                    .envName(envId)
                                    .instanceType(InstanceType.PHYSICAL_HOST_INSTANCE)
                                    .computeProviderId(cloudProviderId)
                                    .computeProviderName(cloudProviderId)
                                    .createdAt(System.currentTimeMillis() - 100000L)
                                    .uuid(instanceId)
                                    .build());
  }

  public void createCloudProvider(String accountId, String appId, String uuid, String name) {
    SettingValue settingValue = PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig()
                                    .withType(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
                                    .build();
    SettingAttribute cloudProvider = SettingAttribute.Builder.aSettingAttribute()
                                         .withName(name)
                                         .withValue(settingValue)
                                         .withUuid(uuid)
                                         .withAccountId(accountId)
                                         .withAppId(appId)
                                         .withCategory(SettingCategory.CLOUD_PROVIDER)
                                         .build();
    settingsService.save(cloudProvider, false);
  }

  public void createConnector(String accountId, String appId, String uuid, String name, SettingValue settingValue) {
    SettingAttribute connector = SettingAttribute.Builder.aSettingAttribute()
                                     .withName(name)
                                     .withValue(settingValue)
                                     .withUuid(uuid)
                                     .withAccountId(accountId)
                                     .withAppId(appId)
                                     .withCategory(SettingCategory.CONNECTOR)
                                     .build();
    settingsService.save(connector, false);
  }

  public void createTag(String accountId, String uuid, String key) {
    HarnessTag tag = HarnessTag.builder().accountId(accountId).key(key).uuid(uuid).build();
    harnessTagService.create(tag);
  }

  public void createCEConnector(String uuid, String accountId, String name, SettingValue settingValue) {
    SettingAttribute ceConnector = SettingAttribute.Builder.aSettingAttribute()
                                       .withName(name)
                                       .withUuid(uuid)
                                       .withValue(settingValue)
                                       .withAccountId(accountId)
                                       .withCategory(SettingCategory.CE_CONNECTOR)
                                       .build();
    hPersistence.save(ceConnector);
  }

  public void createClusterRecord(
      String accountId, String clusterName, String clusterId, String cloudProviderId, String region) {
    Cluster cluster =
        EcsCluster.builder().clusterName(clusterName).cloudProviderId(cloudProviderId).region(region).build();
    clusterRecordService.upsert(
        ClusterRecord.builder().cluster(cluster).accountId(accountId).uuid(clusterId).createdAt(1L).build());
  }

  public void createCECluster(CECluster ceCluster) {
    clusterDao.create(ceCluster);
  }

  public void createCECloudAccount(CECloudAccount ceCloudAccount) {
    cloudAccountDao.create(ceCloudAccount);
  }
}
