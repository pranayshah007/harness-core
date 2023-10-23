/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.seeddata;

import static io.harness.seeddata.SampleDataProviderConstants.HARNESS_SAMPLE_APP;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_SERVICE_INFRA_DEFAULT_NAMESPACE;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.KubernetesConvention;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AuthService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SampleDataProviderServiceImpl implements SampleDataProviderService {
  @Inject private CloudProviderSampleDataProvider cloudProviderSeedDataProvider;
  @Inject private ConnectorSampleDataProvider connectorGenerator;
  @Inject private ApplicationSampleDataProvider applicationSampleDataProvider;
  @Inject private ServiceSampleDataProvider serviceSampleDataProvider;
  @Inject private ArtifactStreamSampleDataProvider artifactStreamSampleDataProvider;
  @Inject private EnvironmentSampleDataProvider environmentSampleDataProvider;
  @Inject private InfraMappingSampleDataProvider infraMappingSampleDataProvider;
  @Inject private InfraDefinitionSampleDataProvider infraDefinitionSampleDataProvider;
  @Inject private WorkflowSampleDataProvider workflowSampleDataProvider;
  @Inject private PipelineSampleDataProvider pipelineSampleDataProvider;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private AuthService authService;

  @Override
  public void createHarnessSampleApp(Account account) {
    try {
      // The following steps to achieve end to end seed data generation
      // Create Cloud Provider ->
      SettingAttribute kubernetesClusterConfig =
          cloudProviderSeedDataProvider.createKubernetesClusterConfig(account.getUuid());

      // Create Docker ConnectorDisconnectHandler
      SettingAttribute dockerConnector = connectorGenerator.createDockerConnector(account.getUuid());

      createK8sSampleApp(account, kubernetesClusterConfig, dockerConnector);
    } catch (Exception ex) {
      log.error("Failed to create Sample Application for the account [" + account.getUuid()
              + "]. Reason: " + ExceptionUtils.getMessage(ex),
          ex);
    }
  }

  private void createK8sSampleApp(
      Account account, SettingAttribute kubernetesClusterConfig, SettingAttribute dockerConnector) {
    // Create App
    Application kubernetesApp = applicationSampleDataProvider.createKubernetesApp(account.getUuid());
    notNullCheck("Kubernetes App not saved", kubernetesApp);

    // Create Service
    Service kubeService = serviceSampleDataProvider.createKubeService(kubernetesApp.getUuid());

    // Create Artifact Stream
    artifactStreamSampleDataProvider.createDockerArtifactStream(
        kubernetesApp.getAppId(), kubeService.getUuid(), dockerConnector);
    // Create QA Environment
    Environment qaEnv = environmentSampleDataProvider.createQAEnvironment(kubernetesApp.getUuid());

    // Create QA Service Infrastructure
    InfrastructureMapping qaInfraMapping = infraMappingSampleDataProvider.createKubeServiceInfraStructure(
        account.getUuid(), kubernetesApp.getUuid(), qaEnv.getUuid(), kubeService.getUuid(),
        kubernetesClusterConfig.getUuid(), K8S_SERVICE_INFRA_DEFAULT_NAMESPACE);

    // Create Prod Environment
    Environment prodEnv = environmentSampleDataProvider.createProdEnvironment(kubernetesApp.getUuid());

    // Create Prod Service Infrastructure
    InfrastructureMapping prodInfraMapping = infraMappingSampleDataProvider.createKubeServiceInfraStructure(
        account.getUuid(), kubernetesApp.getUuid(), prodEnv.getUuid(), kubeService.getUuid(),
        kubernetesClusterConfig.getUuid(), K8S_SERVICE_INFRA_DEFAULT_NAMESPACE);

    // Create Workflow
    String basicWorkflowId = workflowSampleDataProvider.createK8sBasicWorkflow(
        kubernetesApp.getUuid(), qaEnv.getUuid(), kubeService.getUuid(), qaInfraMapping.getUuid());

    // Create Canary Workflow
    String canaryWorkflowId = workflowSampleDataProvider.createK8sCanaryWorkflow(
        kubernetesApp.getUuid(), prodEnv.getUuid(), kubeService.getUuid(), prodInfraMapping.getUuid());

    // Create a Pipeline
    pipelineSampleDataProvider.createPipeline(kubernetesApp.getAccountId(), kubernetesApp.getUuid(), basicWorkflowId,
        qaEnv.getUuid(), canaryWorkflowId, prodEnv.getUuid());
  }

  public void createK8sV2SampleApp(Account account) {
    try {
      // The following steps to achieve end to end seed data generation
      // Create Cloud Provider ->
      SettingAttribute kubernetesClusterConfig =
          cloudProviderSeedDataProvider.createKubernetesClusterConfig(account.getUuid());

      // Create Docker ConnectorDisconnectHandler
      SettingAttribute dockerConnector = connectorGenerator.createDockerConnector(account.getUuid());

      createK8sV2SampleApp(account, kubernetesClusterConfig, dockerConnector, HARNESS_SAMPLE_APP);
      authService.evictUserPermissionAndRestrictionCacheForAccount(account.getUuid(), true, true);
    } catch (Exception ex) {
      String errorMessage = "Failed to create Sample Application for the account [" + account.getUuid() + "]";
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMessage, WingsException.USER, ex)
          .addParam("message", errorMessage);
    }
  }

  private void createK8sV2SampleApp(
      Account account, SettingAttribute kubernetesClusterConfig, SettingAttribute dockerConnector, String appName) {
    Application kubernetesApp = applicationSampleDataProvider.createApp(account.getUuid(), appName, appName);
    notNullCheck("Kubernetes App not saved", kubernetesApp);

    Service kubeService = serviceSampleDataProvider.createK8sV2Service(kubernetesApp.getUuid());

    artifactStreamSampleDataProvider.createDockerArtifactStream(
        kubernetesApp.getAppId(), kubeService.getUuid(), dockerConnector);

    Environment qaEnv = environmentSampleDataProvider.createQAEnvironment(kubernetesApp.getUuid());
    Environment prodEnv = environmentSampleDataProvider.createProdEnvironment(kubernetesApp.getUuid());

    String namespace = "account-" + KubernetesConvention.getAccountIdentifier(account.getUuid());
    String basicWorkflowId;
    String canaryWorkflowId;

    InfrastructureDefinition qaInfraDefinition = infraDefinitionSampleDataProvider.createInfraStructure(
        kubernetesApp.getUuid(), qaEnv.getUuid(), kubernetesClusterConfig.getUuid(), namespace);

    InfrastructureDefinition prodInfraDefinition = infraDefinitionSampleDataProvider.createInfraStructure(
        kubernetesApp.getUuid(), prodEnv.getUuid(), kubernetesClusterConfig.getUuid(), namespace);

    basicWorkflowId = workflowSampleDataProvider.createK8sV2RollingWorkflow(
        kubernetesApp.getUuid(), qaEnv.getUuid(), kubeService.getUuid(), null, qaInfraDefinition.getUuid());

    canaryWorkflowId = workflowSampleDataProvider.createK8sV2CanaryWorkflow(
        kubernetesApp.getUuid(), prodEnv.getUuid(), kubeService.getUuid(), null, prodInfraDefinition.getUuid());

    pipelineSampleDataProvider.createPipeline(kubernetesApp.getAccountId(), kubernetesApp.getUuid(), basicWorkflowId,
        qaEnv.getUuid(), canaryWorkflowId, prodEnv.getUuid());
  }
}
