/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.RESOUCE_GROUP_SERVICE;

import io.harness.accesscontrol.AccessControlAdminClientModule;
import io.harness.account.AccountClient;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.ceviewfolder.CEViewFolderClient;
import io.harness.ccm.ceviewfolder.CEViewFolderClientModule;
import io.harness.ccm.governance.GovernanceRuleClient;
import io.harness.ccm.governance.GovernanceRuleClientModule;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.delegate.DelegateServiceResourceClient;
import io.harness.delegate.DelegateServiceResourceClientModule;
import io.harness.envgroup.EnvironmentGroupResourceClientModule;
import io.harness.envgroup.remote.EnvironmentGroupResourceClient;
import io.harness.environment.EnvironmentResourceClientModule;
import io.harness.filestore.FileStoreClientModule;
import io.harness.gitops.GitopsResourceClientModule;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.organization.remote.OrganizationClient;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineRemoteClientModule;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.project.ProjectClientModule;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceTypeService;
import io.harness.resourcegroup.framework.v1.service.impl.ResourceGroupEventHandler;
import io.harness.resourcegroup.framework.v1.service.impl.ResourceTypeServiceImpl;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupServiceImpl;
import io.harness.resourcegroupclient.ResourceGroupClientModule;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.service.ServiceResourceClientModule;
import io.harness.serviceaccount.ServiceAccountClientModule;
import io.harness.template.TemplateResourceClientModule;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.usergroups.UserGroupClient;
import io.harness.usergroups.UserGroupClientModule;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.reflections.Reflections;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@OwnedBy(PL)
public class ResourceGroupModule extends AbstractModule {
  public static final String RESOURCECLIENT_PACKAGE = "io.harness.resourcegroup.resourceclient";
  ResourceGroupServiceConfig resourceGroupServiceConfig;

  public ResourceGroupModule(ResourceGroupServiceConfig resourceGroupServiceConfig) {
    this.resourceGroupServiceConfig = resourceGroupServiceConfig;
  }

  @Override
  protected void configure() {
    install(new AccessControlAdminClientModule(
        resourceGroupServiceConfig.getAccessControlAdminClientConfiguration(), RESOUCE_GROUP_SERVICE.toString()));
    bind(io.harness.resourcegroup.framework.v1.service.ResourceGroupService.class)
        .to(io.harness.resourcegroup.framework.v1.service.impl.ResourceGroupServiceImpl.class);
    bind(ResourceGroupService.class).to(ResourceGroupServiceImpl.class);
    bind(ResourceTypeService.class).to(ResourceTypeServiceImpl.class);
    bind(String.class).annotatedWith(Names.named("serviceId")).toInstance(RESOUCE_GROUP_SERVICE.toString());
    bind(OutboxEventHandler.class).to(ResourceGroupEventHandler.class);
    install(NGMigrationSdkModule.getInstance());
    requireBinding(OutboxService.class);
    installResourceValidators();
    addResourceValidatorConstraints();
  }

  @Provides
  public Map<String, Resource> getResourceMap(Injector injector) {
    Reflections reflections = new Reflections(RESOURCECLIENT_PACKAGE);
    Set<Class<? extends Resource>> resources = reflections.getSubTypesOf(Resource.class);
    Map<String, Resource> resourceMap = new HashMap<>();
    for (Class<? extends Resource> clz : resources) {
      Resource resource = injector.getInstance(clz);
      resourceMap.put(resource.getType(), resource);
    }
    return resourceMap;
  }

  private void addResourceValidatorConstraints() {
    requireBinding(ProjectClient.class);
    requireBinding(OrganizationClient.class);
    requireBinding(SecretNGManagerClient.class);
    requireBinding(ConnectorResourceClient.class);
    requireBinding(PipelineServiceClient.class);
    requireBinding(UserGroupClient.class);
    requireBinding(ResourceGroupClient.class);
    requireBinding(AccountClient.class);
    requireBinding(DelegateServiceResourceClient.class);
    requireBinding(TemplateResourceClient.class);
    requireBinding(GitopsResourceClient.class);
    requireBinding(EnvironmentGroupResourceClient.class);
    requireBinding(CEViewFolderClient.class);
    requireBinding(GovernanceRuleClient.class);
  }

  private void installResourceValidators() {
    io.harness.resourcegroup.ResourceClientConfigs resourceClients =
        resourceGroupServiceConfig.getResourceClientConfigs();
    ServiceHttpClientConfig ngManagerHttpClientConfig =
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getNgManager().getBaseUrl()).build();
    String ngManagerSecret = resourceClients.getNgManager().getSecret();
    install(new ProjectClientModule(ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(
        new ServiceAccountClientModule(ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new OrganizationClientModule(ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new UserGroupClientModule(ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new ResourceGroupClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getResourceGroupService().getBaseUrl()).build(),
        resourceClients.getResourceGroupService().getSecret(), RESOUCE_GROUP_SERVICE.toString()));
    install(
        new SecretNGManagerClientModule(ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new ConnectorResourceClientModule(
        ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString(), ClientMode.PRIVILEGED));
    install(new AccountClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getManager().getBaseUrl()).build(),
        resourceClients.getManager().getSecret(), RESOUCE_GROUP_SERVICE.toString()));
    install(new DelegateServiceResourceClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getManager().getBaseUrl()).build(),
        resourceClients.getManager().getSecret(), RESOUCE_GROUP_SERVICE.toString()));
    install(new PipelineRemoteClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getPipelineService().getBaseUrl()).build(),
        resourceClients.getPipelineService().getSecret(), RESOUCE_GROUP_SERVICE.toString()));
    install(
        new ServiceResourceClientModule(ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new EnvironmentResourceClientModule(
        ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new EnvironmentGroupResourceClientModule(
        ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new TemplateResourceClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getTemplateService().getBaseUrl()).build(),
        resourceClients.getTemplateService().getSecret(), RESOUCE_GROUP_SERVICE.toString()));
    install(new GitopsResourceClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getGitopsService().getBaseUrl()).build(),
        resourceClients.getGitopsService().getSecret(), RESOUCE_GROUP_SERVICE.toString()));
    install(new FileStoreClientModule(ngManagerHttpClientConfig, ngManagerSecret, RESOUCE_GROUP_SERVICE.toString()));
    install(new CEViewFolderClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getCeNextGen().getBaseUrl()).build(),
        resourceClients.getCeNextGen().getSecret(), RESOUCE_GROUP_SERVICE.toString(), ClientMode.PRIVILEGED));
    install(new GovernanceRuleClientModule(
        ServiceHttpClientConfig.builder().baseUrl(resourceClients.getCeNextGen().getBaseUrl()).build(),
        resourceClients.getCeNextGen().getSecret(), RESOUCE_GROUP_SERVICE.toString(), ClientMode.PRIVILEGED));
  }
}
