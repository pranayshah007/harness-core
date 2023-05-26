/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.authorization.AuthorizationServiceHeader.CE_NEXT_GEN;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.client.DockerRestClientFactory;
import io.harness.artifacts.docker.client.DockerRestClientFactoryImpl;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.impl.AzureAuthorizationClientImpl;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.impl.ConnectorActivityServiceImpl;
import io.harness.connector.impl.ConnectorFilterServiceImpl;
import io.harness.connector.impl.ConnectorHeartbeatServiceImpl;
import io.harness.connector.impl.DefaultConnectorServiceImpl;
import io.harness.connector.impl.GoogleSecretManagerConnectorServiceImpl;
import io.harness.connector.impl.NGConnectorSecretManagerServiceImpl;
import io.harness.connector.impl.NGHostServiceImpl;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.connector.mappers.filter.ConnectorFilterPropertiesMapper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.git.NGGitServiceImpl;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.connector.services.ConnectorActivityService;
import io.harness.connector.services.ConnectorFilterService;
import io.harness.connector.services.ConnectorHeartbeatService;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.GoogleSecretManagerConnectorService;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.connector.services.NGHostService;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.task.scm.ScmDelegateClientImpl;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.gcp.client.GcpClient;
import io.harness.gcp.impl.GcpClientImpl;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.accountsetting.services.NGAccountSettingServiceImpl;
import io.harness.pcf.CfCliClient;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.CfDeploymentManagerImpl;
import io.harness.pcf.CfSdkClient;
import io.harness.pcf.cfcli.client.CfCliClientImpl;
import io.harness.pcf.cfsdk.CfSdkClientImpl;
import io.harness.persistence.HPersistence;
import io.harness.rancher.RancherHelperService;
import io.harness.rancher.RancherHelperServiceImpl;
import io.harness.service.ScmServiceClient;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.SpotInstHelperServiceDelegateImpl;
import io.harness.terraformcloud.TerraformCloudClient;
import io.harness.terraformcloud.TerraformCloudClientImpl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;

@OwnedBy(DX)
public class ConnectorModule extends AbstractModule {
  private static volatile ConnectorModule instance;
  public static final String DEFAULT_CONNECTOR_SERVICE = "defaultConnectorService";
  io.harness.remote.NextGenConfig nextGenConfig;
  io.harness.remote.client.ServiceHttpClientConfig ceNextGenClientConfig;

  //  private ConnectorModule() {}
  private ConnectorModule(io.harness.remote.NextGenConfig nextGenConfig,
      io.harness.remote.client.ServiceHttpClientConfig ceNextGenClientConfig) {
    this.nextGenConfig = nextGenConfig;
    this.ceNextGenClientConfig = ceNextGenClientConfig;
  }

  public static ConnectorModule getInstance(io.harness.remote.NextGenConfig nextGenConfig,
      io.harness.remote.client.ServiceHttpClientConfig ceNextGenClientConfig) {
    if (instance == null) {
      // instance = new ConnectorModule();
      instance = new ConnectorModule(nextGenConfig, ceNextGenClientConfig);
    }

    return instance;
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    install(FiltersModule.getInstance());
    install(new io.harness.ccm.manager.CENextGenResourceClientModule(
        this.ceNextGenClientConfig, this.nextGenConfig.getCeNextGenServiceSecret(), CE_NEXT_GEN.getServiceId()));
    MapBinder<String, ConnectorEntityToDTOMapper> connectorEntityToDTOMapper =
        MapBinder.newMapBinder(binder(), String.class, ConnectorEntityToDTOMapper.class);
    MapBinder<String, ConnectorDTOToEntityMapper> connectorDTOToEntityMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ConnectorDTOToEntityMapper.class);
    MapBinder<String, ConnectionValidator> connectorValidatorMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ConnectionValidator.class);
    MapBinder<String, ConnectorValidationParamsProvider> connectorValidationProviderMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ConnectorValidationParamsProvider.class);
    MapBinder<String, ConnectorValidationHandler> connectorValidationHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ConnectorValidationHandler.class);

    for (ConnectorType connectorType : ConnectorType.values()) {
      connectorValidatorMapBinder.addBinding(connectorType.getDisplayName())
          .to(ConnectorRegistryFactory.getConnectorValidator(connectorType));
      connectorDTOToEntityMapBinder.addBinding(connectorType.getDisplayName())
          .to(ConnectorRegistryFactory.getConnectorDTOToEntityMapper(connectorType));
      connectorEntityToDTOMapper.addBinding(connectorType.getDisplayName())
          .to(ConnectorRegistryFactory.getConnectorEntityToDTOMapper(connectorType));
      connectorValidationProviderMapBinder.addBinding(connectorType.getDisplayName())
          .to(ConnectorRegistryFactory.getConnectorValidationParamsProvider(connectorType));
      connectorValidationHandlerMapBinder.addBinding(connectorType.getDisplayName())
          .to(ConnectorRegistryFactory.getConnectorValidationHandler(connectorType));
    }
    bind(ConnectorService.class)
        .annotatedWith(Names.named(DEFAULT_CONNECTOR_SERVICE))
        .to(DefaultConnectorServiceImpl.class);
    bind(ConnectorActivityService.class).to(ConnectorActivityServiceImpl.class);
    bind(ConnectorFilterService.class).to(ConnectorFilterServiceImpl.class);
    bind(ConnectorHeartbeatService.class).to(ConnectorHeartbeatServiceImpl.class);
    bind(AwsClient.class).to(AwsClientImpl.class);
    bind(GcpClient.class).to(GcpClientImpl.class);
    bind(AzureAuthorizationClient.class).to(AzureAuthorizationClientImpl.class);
    bind(SpotInstHelperServiceDelegate.class).to(SpotInstHelperServiceDelegateImpl.class);
    bind(CfDeploymentManager.class).to(CfDeploymentManagerImpl.class);
    bind(CfCliClient.class).to(CfCliClientImpl.class);
    bind(CfSdkClient.class).to(CfSdkClientImpl.class);
    bind(NGGitService.class).to(NGGitServiceImpl.class);
    bind(DockerRestClientFactory.class).to(DockerRestClientFactoryImpl.class);
    bind(GitClientV2.class).to(GitClientV2Impl.class);
    bind(ScmDelegateClient.class).to(ScmDelegateClientImpl.class);
    bind(NGConnectorSecretManagerService.class).to(NGConnectorSecretManagerServiceImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(NGAccountSettingService.class).to(NGAccountSettingServiceImpl.class);
    bind(NGHostService.class).to(NGHostServiceImpl.class);
    bind(GoogleSecretManagerConnectorService.class).to(GoogleSecretManagerConnectorServiceImpl.class);
    bind(TerraformCloudClient.class).to(TerraformCloudClientImpl.class);
    bind(RancherHelperService.class).to(RancherHelperServiceImpl.class);
    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.CONNECTOR.toString()).to(ConnectorFilterPropertiesMapper.class);
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
