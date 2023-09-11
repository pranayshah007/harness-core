/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.setup.graphql.CeConnectorDataFetcher;
import io.harness.ccm.setup.graphql.EksClusterStatsDataFetcher;
import io.harness.ccm.setup.graphql.InfraAccountConnectionDataFetcher;
import io.harness.ccm.setup.graphql.LinkedAccountStatsDataFetcher;
import io.harness.ccm.setup.graphql.OverviewPageStatsDataFetcher;
import io.harness.ccm.views.graphql.ViewEntityStatsDataFetcher;
import io.harness.ccm.views.graphql.ViewFieldsDataFetcher;
import io.harness.ccm.views.graphql.ViewFilterStatsDataFetcher;
import io.harness.ccm.views.graphql.ViewOverviewStatsDataFetcher;
import io.harness.ccm.views.graphql.ViewTimeSeriesStatsDataFetcher;
import io.harness.ccm.views.graphql.ViewTrendStatsDataFetcher;
import io.harness.ccm.views.graphql.ViewsDataFetcher;

import software.wings.beans.infrastructure.instance.info.AutoScalingGroupInstanceInfo;
import software.wings.beans.infrastructure.instance.info.CodeDeployInstanceInfo;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;
import software.wings.graphql.datafetcher.DataLoaderRegistryHelper;
import software.wings.graphql.datafetcher.anomaly.CloudAnomaliesDataFetcher;
import software.wings.graphql.datafetcher.anomaly.K8sAnomaliesDataFetcher;
import software.wings.graphql.datafetcher.anomaly.OverviewAnomaliesDataFetcher;
import software.wings.graphql.datafetcher.anomaly.UpdateAnomalyDataFetcher;
import software.wings.graphql.datafetcher.application.ApplicationConnectionDataFetcher;
import software.wings.graphql.datafetcher.application.ApplicationDataFetcher;
import software.wings.graphql.datafetcher.application.ApplicationGitSyncConfigDataFetcher;
import software.wings.graphql.datafetcher.application.ApplicationStatsDataFetcher;
import software.wings.graphql.datafetcher.application.CreateApplicationDataFetcher;
import software.wings.graphql.datafetcher.application.DeleteApplicationDataFetcher;
import software.wings.graphql.datafetcher.application.RemoveApplicationGitSyncConfigDataFetcher;
import software.wings.graphql.datafetcher.application.UpdateApplicationDataFetcher;
import software.wings.graphql.datafetcher.application.UpdateApplicationGitSyncConfigDataFetcher;
import software.wings.graphql.datafetcher.application.UpdateApplicationGitSyncConfigStatusDataFetcher;
import software.wings.graphql.datafetcher.application.batch.ApplicationBatchDataFetcher;
import software.wings.graphql.datafetcher.application.batch.ApplicationBatchDataLoader;
import software.wings.graphql.datafetcher.artifactSource.ArtifactSourceDataFetcher;
import software.wings.graphql.datafetcher.artifactSource.ServiceArtifactSourceConnectionDataFetcher;
import software.wings.graphql.datafetcher.artifactSource.batch.ArtifactSourceBatchDataFetcher;
import software.wings.graphql.datafetcher.artifactSource.batch.ArtifactSourceBatchDataLoader;
import software.wings.graphql.datafetcher.audit.ChangeContentConnectionDataFetcher;
import software.wings.graphql.datafetcher.audit.ChangeSetConnectionDataFetcher;
import software.wings.graphql.datafetcher.billing.BillingForecastCostDataFetcher;
import software.wings.graphql.datafetcher.billing.BillingJobProcessedDataFetcher;
import software.wings.graphql.datafetcher.billing.BillingStatsEntityDataFetcher;
import software.wings.graphql.datafetcher.billing.BillingStatsFilterValuesDataFetcher;
import software.wings.graphql.datafetcher.billing.BillingStatsTimeSeriesDataFetcher;
import software.wings.graphql.datafetcher.billing.BillingTrendStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.CloudEntityStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.CloudFilterValuesDataFetcher;
import software.wings.graphql.datafetcher.billing.CloudOverviewDataFetcher;
import software.wings.graphql.datafetcher.billing.CloudTimeSeriesStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.CloudTrendStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.EfficiencyStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.GcpBillingAccountDataFetcher;
import software.wings.graphql.datafetcher.billing.GcpBillingEntityStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.GcpBillingTimeSeriesStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.GcpBillingTrendStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.GcpOrganizationDataFetcher;
import software.wings.graphql.datafetcher.billing.GcpServiceAccountDataFetcher;
import software.wings.graphql.datafetcher.billing.IdleCostTrendStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.NodeAndPodDetailsDataFetcher;
import software.wings.graphql.datafetcher.billing.SunburstChartStatsDataFetcher;
import software.wings.graphql.datafetcher.budget.BudgetDataFetcher;
import software.wings.graphql.datafetcher.budget.BudgetListDataFetcher;
import software.wings.graphql.datafetcher.budget.BudgetNotificationsDataFetcher;
import software.wings.graphql.datafetcher.budget.BudgetTrendStatsDataFetcher;
import software.wings.graphql.datafetcher.ce.activePods.CeActivePodCountDataFetcher;
import software.wings.graphql.datafetcher.ce.exportData.CeClusterBillingDataDataFetcher;
import software.wings.graphql.datafetcher.ce.recommendation.K8sWorkloadHistogramDataFetcher;
import software.wings.graphql.datafetcher.ce.recommendation.K8sWorkloadRecommendationsDataFetcher;
import software.wings.graphql.datafetcher.cloudProvider.CloudProviderConnectionDataFetcher;
import software.wings.graphql.datafetcher.cloudProvider.CloudProviderDataFetcher;
import software.wings.graphql.datafetcher.cloudProvider.CloudProviderStatsDataFetcher;
import software.wings.graphql.datafetcher.cloudProvider.CreateCloudProviderDataFetcher;
import software.wings.graphql.datafetcher.cloudProvider.DeleteCloudProviderDataFetcher;
import software.wings.graphql.datafetcher.cloudProvider.UpdateCloudProviderDataFetcher;
import software.wings.graphql.datafetcher.cloudefficiencyevents.EventsStatsDataFetcher;
import software.wings.graphql.datafetcher.cloudefficiencyevents.K8sEventYamlDiffDataFetcher;
import software.wings.graphql.datafetcher.cluster.ClusterConnectionDataFetcher;
import software.wings.graphql.datafetcher.cluster.ClusterDataFetcher;
import software.wings.graphql.datafetcher.connector.ConnectorConnectionDataFetcher;
import software.wings.graphql.datafetcher.connector.ConnectorDataFetcher;
import software.wings.graphql.datafetcher.connector.ConnectorStatsDataFetcher;
import software.wings.graphql.datafetcher.connector.CreateConnectorDataFetcher;
import software.wings.graphql.datafetcher.connector.DeleteConnectorDataFetcher;
import software.wings.graphql.datafetcher.connector.UpdateConnectorDataFetcher;
import software.wings.graphql.datafetcher.cv.VerificationResultConnectionDataFetcher;
import software.wings.graphql.datafetcher.cv.VerificationStatsDataFetcher;
import software.wings.graphql.datafetcher.environment.EnvironmentConnectionDataFetcher;
import software.wings.graphql.datafetcher.environment.EnvironmentDataFetcher;
import software.wings.graphql.datafetcher.environment.EnvironmentStatsDataFetcher;
import software.wings.graphql.datafetcher.environment.batch.EnvironmentBatchDataFetcher;
import software.wings.graphql.datafetcher.environment.batch.EnvironmentBatchDataLoader;
import software.wings.graphql.datafetcher.execution.ExecutionConnectionDataFetcher;
import software.wings.graphql.datafetcher.execution.ExecutionDataFetcher;
import software.wings.graphql.datafetcher.execution.ExecutionInputsDataFetcher;
import software.wings.graphql.datafetcher.execution.ExportExecutionsDataFetcher;
import software.wings.graphql.datafetcher.execution.PipelineResumeRuntimeDataFetcher;
import software.wings.graphql.datafetcher.execution.ResumeExecutionDataFetcher;
import software.wings.graphql.datafetcher.execution.RuntimeExecutionInputsToResumePipelineDataFetcher;
import software.wings.graphql.datafetcher.execution.StartExecutionDataFetcher;
import software.wings.graphql.datafetcher.frozenExecution.FrozenExecutionConnectionDataFetcher;
import software.wings.graphql.datafetcher.infraDefinition.InfrastructureDefinitionConnectionDataFetcher;
import software.wings.graphql.datafetcher.infraDefinition.InfrastructureDefinitionDataFetcher;
import software.wings.graphql.datafetcher.infraDefinition.batch.InfrastructureDefinitionBatchDataFetcher;
import software.wings.graphql.datafetcher.infraDefinition.batch.InfrastructureDefinitionBatchDataLoader;
import software.wings.graphql.datafetcher.instance.InstanceConnectionDataFetcher;
import software.wings.graphql.datafetcher.instance.InstanceCountDataFetcher;
import software.wings.graphql.datafetcher.instance.instanceInfo.AutoScalingGroupInstanceController;
import software.wings.graphql.datafetcher.instance.instanceInfo.CodeDeployInstanceController;
import software.wings.graphql.datafetcher.instance.instanceInfo.Ec2InstanceController;
import software.wings.graphql.datafetcher.instance.instanceInfo.EcsContainerController;
import software.wings.graphql.datafetcher.instance.instanceInfo.InstanceController;
import software.wings.graphql.datafetcher.instance.instanceInfo.K8SPodController;
import software.wings.graphql.datafetcher.instance.instanceInfo.KubernetesContainerController;
import software.wings.graphql.datafetcher.instance.instanceInfo.PcfInstanceController;
import software.wings.graphql.datafetcher.instance.instanceInfo.PhysicalHostInstanceController;
import software.wings.graphql.datafetcher.k8sLabel.K8sLabelConnectionDataFetcher;
import software.wings.graphql.datafetcher.outcome.OutcomeConnectionDataFetcher;
import software.wings.graphql.datafetcher.pipeline.PipelineConnectionDataFetcher;
import software.wings.graphql.datafetcher.pipeline.PipelineDataFetcher;
import software.wings.graphql.datafetcher.pipeline.PipelineStatsDataFetcher;
import software.wings.graphql.datafetcher.pipeline.PipelineVariableConnectionDataFetcher;
import software.wings.graphql.datafetcher.pipeline.batch.PipelineBatchDataFetcher;
import software.wings.graphql.datafetcher.pipeline.batch.PipelineBatchDataLoader;
import software.wings.graphql.datafetcher.secretManager.CreateSecretManagerDataFetcher;
import software.wings.graphql.datafetcher.secretManager.DeleteSecretManagerDataFetcher;
import software.wings.graphql.datafetcher.secretManager.SecretManagerDataFetcher;
import software.wings.graphql.datafetcher.secretManager.SecretManagersDataFetcher;
import software.wings.graphql.datafetcher.secretManager.UpdateSecretManagerDataFetcher;
import software.wings.graphql.datafetcher.secrets.CreateSecretDataFetcher;
import software.wings.graphql.datafetcher.secrets.DeleteSecretDataFetcher;
import software.wings.graphql.datafetcher.secrets.GetSecretDataFetcher;
import software.wings.graphql.datafetcher.secrets.UpdateSecretDataFetcher;
import software.wings.graphql.datafetcher.service.ServiceConnectionDataFetcher;
import software.wings.graphql.datafetcher.service.ServiceDataFetcher;
import software.wings.graphql.datafetcher.service.ServiceStatsDataFetcher;
import software.wings.graphql.datafetcher.service.UpdateServiceMetadataDataFetcher;
import software.wings.graphql.datafetcher.service.batch.ServiceBatchDataFetcher;
import software.wings.graphql.datafetcher.service.batch.ServiceBatchDataLoader;
import software.wings.graphql.datafetcher.ssoProvider.SsoProviderConnectionDataFetcher;
import software.wings.graphql.datafetcher.ssoProvider.SsoProviderDataFetcher;
import software.wings.graphql.datafetcher.tag.AttachTagDataFetcher;
import software.wings.graphql.datafetcher.tag.DetachTagDataFetcher;
import software.wings.graphql.datafetcher.tag.TagConnectionDataFetcher;
import software.wings.graphql.datafetcher.tag.TagDataFetcher;
import software.wings.graphql.datafetcher.tag.TagUsageConnectionDataFetcher;
import software.wings.graphql.datafetcher.tag.TagsDataFetcher;
import software.wings.graphql.datafetcher.tag.TagsInUseConnectionDataFetcher;
import software.wings.graphql.datafetcher.user.CreateUserDataFetcher;
import software.wings.graphql.datafetcher.user.DeleteUserDataFetcher;
import software.wings.graphql.datafetcher.user.UpdateUserDataFetcher;
import software.wings.graphql.datafetcher.user.UserConnectionDataFetcher;
import software.wings.graphql.datafetcher.user.UserDataFetcher;
import software.wings.graphql.datafetcher.userGroup.AddAccountPermissionDataFetcher;
import software.wings.graphql.datafetcher.userGroup.AddAppPermissionDataFetcher;
import software.wings.graphql.datafetcher.userGroup.AddUserToUserGroupDataFetcher;
import software.wings.graphql.datafetcher.userGroup.CreateUserGroupDataFetcher;
import software.wings.graphql.datafetcher.userGroup.DeleteUserGroupDataFetcher;
import software.wings.graphql.datafetcher.userGroup.RemoveUserFromUserGroupDataFetcher;
import software.wings.graphql.datafetcher.userGroup.UpdateUserGroupDataFetcher;
import software.wings.graphql.datafetcher.userGroup.UpdateUserGroupPermissionsDataFetcher;
import software.wings.graphql.datafetcher.userGroup.UserGroupConnectionDataFetcher;
import software.wings.graphql.datafetcher.userGroup.UserGroupDataFetcher;
import software.wings.graphql.datafetcher.workflow.WorkflowConnectionDataFetcher;
import software.wings.graphql.datafetcher.workflow.WorkflowDataFetcher;
import software.wings.graphql.datafetcher.workflow.WorkflowStatsDataFetcher;
import software.wings.graphql.datafetcher.workflow.WorkflowVariableConnectionDataFetcher;
import software.wings.graphql.datafetcher.workflow.batch.WorkflowBatchDataFetcher;
import software.wings.graphql.datafetcher.workflow.batch.WorkflowBatchDataLoader;
import software.wings.graphql.directive.DataFetcherDirective;
import software.wings.graphql.instrumentation.QLAuditInstrumentation;
import software.wings.graphql.provider.GraphQLProvider;
import software.wings.graphql.provider.QueryLanguageProvider;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import java.util.Collections;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.dataloader.MappedBatchLoader;
import org.hibernate.validator.constraints.NotBlank;

/**
 * Created a new module as part of code review comment
 */
@OwnedBy(DX)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@Deprecated
public class WingsGraphQLModule extends AbstractModule {
  /***
   * This collection is mainly required to inject batched loader at app start time.
   * I was not getting a handle to Annotation Name at runtime hence I am taking this approach.
   */
  private static final Set<String> BATCH_DATA_LOADER_NAMES = Sets.newHashSet();
  private static final String DATA_FETCHER_SUFFIX = "DataFetcher";
  public static final String BATCH_SUFFIX = "Batch";
  private static final String BATCH_DATA_LOADER_SUFFIX = BATCH_SUFFIX.concat("DataLoader");

  private static volatile WingsGraphQLModule instance;

  public static WingsGraphQLModule getInstance() {
    if (instance == null) {
      instance = new WingsGraphQLModule();
    }
    return instance;
  }

  private WingsGraphQLModule() {}

  public static Set<String> getBatchDataLoaderNames() {
    return Collections.unmodifiableSet(BATCH_DATA_LOADER_NAMES);
  }

  @Override
  protected void configure() {
    bind(new TypeLiteral<QueryLanguageProvider<GraphQL>>() {}).to(GraphQLProvider.class).asEagerSingleton();
    bind(DataFetcherDirective.class).asEagerSingleton();
    bind(DataLoaderRegistryHelper.class).asEagerSingleton();
    bind(QLAuditInstrumentation.class).asEagerSingleton();
    bind(DataFetcherDirective.class).in(Scopes.SINGLETON);
    bind(DataLoaderRegistryHelper.class).in(Scopes.SINGLETON);
    bind(QLAuditInstrumentation.class).in(Scopes.SINGLETON);

    // DATA FETCHERS ARE NOT SINGLETON AS THEY CAN HAVE DIFFERENT CONTEXT MAP
    bindDataFetchers();

    bindBatchedDataLoaderWithAnnotation();

    bindInstanceInfoControllers();
  }

  private void bindBatchedDataLoaderWithAnnotation() {
    bindBatchedDataLoaderWithAnnotation(ApplicationBatchDataLoader.class);
    bindBatchedDataLoaderWithAnnotation(ServiceBatchDataLoader.class);
    bindBatchedDataLoaderWithAnnotation(EnvironmentBatchDataLoader.class);
    bindBatchedDataLoaderWithAnnotation(ArtifactSourceBatchDataLoader.class);
    bindBatchedDataLoaderWithAnnotation(WorkflowBatchDataLoader.class);
    bindBatchedDataLoaderWithAnnotation(PipelineBatchDataLoader.class);
    bindBatchedDataLoaderWithAnnotation(InfrastructureDefinitionBatchDataLoader.class);
  }

  private void bindInstanceInfoControllers() {
    MapBinder<Class, InstanceController> instanceInfoControllerMapBinder =
        MapBinder.newMapBinder(binder(), Class.class, InstanceController.class);
    instanceInfoControllerMapBinder.addBinding(PhysicalHostInstanceInfo.class).to(PhysicalHostInstanceController.class);
    instanceInfoControllerMapBinder.addBinding(AutoScalingGroupInstanceInfo.class)
        .to(AutoScalingGroupInstanceController.class);
    instanceInfoControllerMapBinder.addBinding(Ec2InstanceInfo.class).to(Ec2InstanceController.class);
    instanceInfoControllerMapBinder.addBinding(CodeDeployInstanceInfo.class).to(CodeDeployInstanceController.class);
    instanceInfoControllerMapBinder.addBinding(EcsContainerInfo.class).to(EcsContainerController.class);
    instanceInfoControllerMapBinder.addBinding(K8sPodInfo.class).to(K8SPodController.class);
    instanceInfoControllerMapBinder.addBinding(KubernetesContainerInfo.class).to(KubernetesContainerController.class);
    instanceInfoControllerMapBinder.addBinding(PcfInstanceInfo.class).to(PcfInstanceController.class);
  }

  private void bindDataFetchers() {
    bindDataFetcherWithAnnotation(ApplicationBatchDataFetcher.class);
    bindDataFetcherWithAnnotation(ApplicationConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ApplicationDataFetcher.class);
    bindDataFetcherWithAnnotation(ApplicationStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ArtifactSourceDataFetcher.class);
    bindDataFetcherWithAnnotation(ArtifactSourceBatchDataFetcher.class);
    bindDataFetcherWithAnnotation(BillingForecastCostDataFetcher.class);
    bindDataFetcherWithAnnotation(BillingJobProcessedDataFetcher.class);
    bindDataFetcherWithAnnotation(BillingStatsEntityDataFetcher.class);
    bindDataFetcherWithAnnotation(BillingStatsFilterValuesDataFetcher.class);
    bindDataFetcherWithAnnotation(BillingStatsTimeSeriesDataFetcher.class);
    bindDataFetcherWithAnnotation(BillingTrendStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(BudgetDataFetcher.class);
    bindDataFetcherWithAnnotation(BudgetListDataFetcher.class);
    bindDataFetcherWithAnnotation(BudgetNotificationsDataFetcher.class);
    bindDataFetcherWithAnnotation(BudgetTrendStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(CeClusterBillingDataDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudProviderConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudProviderDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateCloudProviderDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateCloudProviderDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteCloudProviderDataFetcher.class);
    bindDataFetcherWithAnnotation(ClusterDataFetcher.class);
    bindDataFetcherWithAnnotation(ClusterConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ConnectorConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(CeConnectorDataFetcher.class);
    bindDataFetcherWithAnnotation(CeActivePodCountDataFetcher.class);
    bindDataFetcherWithAnnotation(ConnectorStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudAnomaliesDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudFilterValuesDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudProviderStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudOverviewDataFetcher.class);
    bindDataFetcherWithAnnotation(ConnectorDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateConnectorDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateConnectorDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteConnectorDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudTimeSeriesStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudEntityStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(CloudTrendStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(EfficiencyStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(EnvironmentConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(EnvironmentDataFetcher.class);
    bindDataFetcherWithAnnotation(EnvironmentBatchDataFetcher.class);
    bindDataFetcherWithAnnotation(EnvironmentStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ExecutionConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(FrozenExecutionConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ExecutionDataFetcher.class);
    bindDataFetcherWithAnnotation(GcpBillingAccountDataFetcher.class);
    bindDataFetcherWithAnnotation(GcpBillingTimeSeriesStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(GcpBillingEntityStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(GcpBillingTrendStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(GcpOrganizationDataFetcher.class);
    bindDataFetcherWithAnnotation(GcpServiceAccountDataFetcher.class);
    bindDataFetcherWithAnnotation(IdleCostTrendStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(OverviewAnomaliesDataFetcher.class);
    bindDataFetcherWithAnnotation(OverviewPageStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(InstanceConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(InstanceCountDataFetcher.class);

    bindDataFetcherWithAnnotation(InfrastructureDefinitionDataFetcher.class);
    bindDataFetcherWithAnnotation(InfrastructureDefinitionConnectionDataFetcher.class);

    bindDataFetcherWithAnnotation(LinkedAccountStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(K8sAnomaliesDataFetcher.class);
    bindDataFetcherWithAnnotation(K8sEventYamlDiffDataFetcher.class);
    bindDataFetcherWithAnnotation(K8sLabelConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(K8sWorkloadRecommendationsDataFetcher.class);
    bindDataFetcherWithAnnotation(K8sWorkloadHistogramDataFetcher.class);
    bindDataFetcherWithAnnotation(NodeAndPodDetailsDataFetcher.class);
    bindDataFetcherWithAnnotation(OutcomeConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(PipelineConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(PipelineDataFetcher.class);
    bindDataFetcherWithAnnotation(PipelineBatchDataFetcher.class);
    bindDataFetcherWithAnnotation(PipelineStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(PipelineVariableConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ServiceConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ServiceBatchDataFetcher.class);
    bindDataFetcherWithAnnotation(ServiceDataFetcher.class);
    bindDataFetcherWithAnnotation(ServiceArtifactSourceConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ServiceStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(SunburstChartStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(TagsInUseConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(TagsDataFetcher.class);
    bindDataFetcherWithAnnotation(EksClusterStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(InfraAccountConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewFieldsDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewsDataFetcher.class);
    bindDataFetcherWithAnnotation(WorkflowConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(WorkflowDataFetcher.class);
    bindDataFetcherWithAnnotation(WorkflowBatchDataFetcher.class);
    bindDataFetcherWithAnnotation(WorkflowVariableConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(WorkflowStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(VerificationStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ChangeSetConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ChangeContentConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateApplicationDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateApplicationDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateServiceMetadataDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteApplicationDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateUserGroupPermissionsDataFetcher.class);
    bindDataFetcherWithAnnotation(AddAccountPermissionDataFetcher.class);
    bindDataFetcherWithAnnotation(AddAppPermissionDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateUserDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateUserDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteUserDataFetcher.class);
    bindDataFetcherWithAnnotation(UserGroupDataFetcher.class);
    bindDataFetcherWithAnnotation(UserGroupConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteUserGroupDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateUserGroupDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateUserGroupDataFetcher.class);
    bindDataFetcherWithAnnotation(AddUserToUserGroupDataFetcher.class);
    bindDataFetcherWithAnnotation(RemoveUserFromUserGroupDataFetcher.class);
    bindDataFetcherWithAnnotation(UserDataFetcher.class);
    bindDataFetcherWithAnnotation(UserConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(EventsStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateApplicationGitSyncConfigDataFetcher.class);
    bindDataFetcherWithAnnotation(RemoveApplicationGitSyncConfigDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateAnomalyDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateApplicationGitSyncConfigStatusDataFetcher.class);
    bindDataFetcherWithAnnotation(ApplicationGitSyncConfigDataFetcher.class);
    bindDataFetcherWithAnnotation(SsoProviderDataFetcher.class);
    bindDataFetcherWithAnnotation(SsoProviderConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateSecretDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateSecretDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteSecretDataFetcher.class);
    bindDataFetcherWithAnnotation(SecretManagerDataFetcher.class);
    bindDataFetcherWithAnnotation(SecretManagersDataFetcher.class);
    bindDataFetcherWithAnnotation(GetSecretDataFetcher.class);
    bindDataFetcherWithAnnotation(StartExecutionDataFetcher.class);
    bindDataFetcherWithAnnotation(ResumeExecutionDataFetcher.class);
    bindDataFetcherWithAnnotation(ExecutionInputsDataFetcher.class);
    bindDataFetcherWithAnnotation(ExportExecutionsDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewEntityStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewFilterStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewTimeSeriesStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewTrendStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewFilterStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ViewOverviewStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(PipelineResumeRuntimeDataFetcher.class);
    bindDataFetcherWithAnnotation(RuntimeExecutionInputsToResumePipelineDataFetcher.class);

    bindDataFetcherWithAnnotation(CreateSecretManagerDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteSecretManagerDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateSecretManagerDataFetcher.class);

    bindDataFetcherWithAnnotation(AttachTagDataFetcher.class);
    bindDataFetcherWithAnnotation(DetachTagDataFetcher.class);
    bindDataFetcherWithAnnotation(TagConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(TagUsageConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(TagDataFetcher.class);
    bindDataFetcherWithAnnotation(VerificationResultConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(InfrastructureDefinitionBatchDataFetcher.class);
  }

  @NotNull
  public static String calculateAnnotationName(final Class clazz, String suffixToRemove) {
    String className = clazz.getName();
    char c[] = className.substring(className.lastIndexOf('.') + 1, clazz.getName().length() - suffixToRemove.length())
                   .toCharArray();

    c[0] = Character.toLowerCase(c[0]);
    return new String(c);
  }

  private void bindDataFetcherWithAnnotation(Class<? extends DataFetcher> clazz, String suffix) {
    String annotationName = calculateAnnotationName(clazz, suffix);
    bind(DataFetcher.class).annotatedWith(Names.named(annotationName)).to(clazz);
  }

  private void bindDataFetcherWithAnnotation(Class<? extends DataFetcher> clazz) {
    bindDataFetcherWithAnnotation(clazz, DATA_FETCHER_SUFFIX);
  }

  private void bindBatchedDataLoaderWithAnnotation(Class<? extends MappedBatchLoader> clazz) {
    String annotationName = calculateAnnotationName(clazz, BATCH_DATA_LOADER_SUFFIX);
    BATCH_DATA_LOADER_NAMES.add(annotationName);
    bind(MappedBatchLoader.class).annotatedWith(Names.named(annotationName)).to(clazz).in(Scopes.SINGLETON);
  }

  public static String getBatchDataLoaderAnnotationName(@NotBlank String dataFetcherName) {
    return dataFetcherName.concat(BATCH_SUFFIX);
  }
}
