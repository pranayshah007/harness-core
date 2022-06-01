/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.InfraDefinition;

import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.VMSSAuthType.PASSWORD;
import static software.wings.beans.VMSSAuthType.SSH_PUBLIC_KEY;
import static software.wings.infra.AzureVMSSInfra.Yaml;

import software.wings.beans.InfrastructureType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.infra.AzureVMSSInfra;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.List;

public class AzureVMSSInfraYamlHandler extends CloudProviderInfrastructureYamlHandler<Yaml, AzureVMSSInfra> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;

  @Override
  public Yaml toYaml(AzureVMSSInfra bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    notNullCheck("Cloud provider is NULL", cloudProvider);
    notNullCheck("Subscription id is NULL", bean.getSubscriptionId());
    notNullCheck("Resource group name is NULL", bean.getResourceGroupName());
    notNullCheck("Base scale set name is NULL", bean.getBaseVMSSName());

    String hostConnectionAttributeName = null;
    if (SSH_PUBLIC_KEY == bean.getVmssAuthType()) {
      notNullCheck("Public Key Name is NULL", bean.getHostConnectionAttrs());
      hostConnectionAttributeName = bean.getHostConnectionAttrs();
    }

    String passwordSecretTextName = null;
    if (PASSWORD == bean.getVmssAuthType()) {
      passwordSecretTextName = bean.getPasswordSecretTextName();
      notNullCheck("Password Secret Text Name is NULL", passwordSecretTextName);
    }

    return Yaml.builder()
        .type(InfrastructureType.AZURE_VMSS)
        .cloudProviderName(cloudProvider.getName())
        .baseVMSSName(bean.getBaseVMSSName())
        .userName(bean.getUserName())
        .resourceGroupName(bean.getResourceGroupName())
        .subscriptionId(bean.getSubscriptionId())
        .passwordSecretTextName(bean.getPasswordSecretTextName())
        .hostConnectionAttrs(hostConnectionAttributeName)
        .vmssAuthType(bean.getVmssAuthType())
        .vmssDeploymentType(bean.getVmssDeploymentType())
        .build();
  }

  @Override
  public AzureVMSSInfra upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    AzureVMSSInfra bean = AzureVMSSInfra.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(AzureVMSSInfra bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    notNullCheck("Cloud Provider is NULL", cloudProvider);
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    notNullCheck("Subscription Id is NULL", yaml.getSubscriptionId());
    notNullCheck("Resource Group Name is NULL", yaml.getResourceGroupName());
    notNullCheck("Base Scale Set Name is NULL", yaml.getBaseVMSSName());

    if (SSH_PUBLIC_KEY == yaml.getVmssAuthType()) {
      SettingAttribute attribute = settingsService.getSettingAttributeByName(accountId, yaml.getHostConnectionAttrs());
      notNullCheck("Public Key is NULL", attribute);
    }

    String passwordSecretTextName = null;
    if (PASSWORD == yaml.getVmssAuthType()) {
      passwordSecretTextName = yaml.getPasswordSecretTextName();
      notNullCheck("Password Secret Text Name is NULL", passwordSecretTextName);
    }

    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setHostConnectionAttrs(yaml.getHostConnectionAttrs());
    bean.setPasswordSecretTextName(yaml.getPasswordSecretTextName());
    bean.setBaseVMSSName(yaml.getBaseVMSSName());
    bean.setUserName(yaml.getUserName());
    bean.setResourceGroupName(yaml.getResourceGroupName());
    bean.setSubscriptionId(yaml.getSubscriptionId());
    bean.setVmssAuthType(yaml.getVmssAuthType());
    bean.setVmssDeploymentType(yaml.getVmssDeploymentType());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
