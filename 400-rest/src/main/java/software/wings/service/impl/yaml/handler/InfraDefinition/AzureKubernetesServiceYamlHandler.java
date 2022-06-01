/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.InfraDefinition;

import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import software.wings.beans.InfrastructureType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.infra.AzureKubernetesService;
import software.wings.infra.AzureKubernetesService.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class AzureKubernetesServiceYamlHandler
    extends CloudProviderInfrastructureYamlHandler<Yaml, AzureKubernetesService> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;

  @Override
  public Yaml toYaml(AzureKubernetesService bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    return Yaml.builder()
        .clusterName(bean.getClusterName())
        .namespace(bean.getNamespace())
        .releaseName(bean.getReleaseName())
        .resourceGroup(bean.getResourceGroup())
        .subscriptionId(bean.getSubscriptionId())
        .cloudProviderName(cloudProvider.getName())
        .type(InfrastructureType.AZURE_KUBERNETES)
        .build();
  }

  @Override
  public AzureKubernetesService upsertFromYaml(
      ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    AzureKubernetesService bean = AzureKubernetesService.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(AzureKubernetesService bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    bean.setClusterName(yaml.getClusterName());
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setNamespace(yaml.getNamespace());
    bean.setReleaseName(yaml.getReleaseName());
    bean.setResourceGroup(yaml.getResourceGroup());
    bean.setSubscriptionId(yaml.getSubscriptionId());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
