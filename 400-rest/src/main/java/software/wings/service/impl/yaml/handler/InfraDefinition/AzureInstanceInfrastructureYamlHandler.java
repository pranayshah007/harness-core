/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.InfraDefinition;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.exception.WingsException;

import software.wings.beans.InfrastructureType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.AzureInstanceInfrastructure.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class AzureInstanceInfrastructureYamlHandler
    extends CloudProviderInfrastructureYamlHandler<Yaml, AzureInstanceInfrastructure> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;

  @Override
  public Yaml toYaml(AzureInstanceInfrastructure bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    SettingAttribute hostConnectionAttr = settingsService.get(bean.getHostConnectionAttrs());
    SettingAttribute winRmConnectionAttr = settingsService.get(bean.getWinRmConnectionAttributes());
    Yaml yaml = Yaml.builder()
                    .resourceGroup(bean.getResourceGroup())
                    .subscriptionId(bean.getSubscriptionId())
                    .tags(bean.getTags())
                    .cloudProviderName(cloudProvider.getName())
                    .type(InfrastructureType.AZURE_SSH)
                    .build();

    if (hostConnectionAttr != null) {
      yaml.setHostConnectionAttrsName(hostConnectionAttr.getName());
    } else if (winRmConnectionAttr != null) {
      yaml.setWinRmConnectionAttributesName(winRmConnectionAttr.getName());
    }
    return yaml;
  }

  @Override
  public AzureInstanceInfrastructure upsertFromYaml(
      ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    AzureInstanceInfrastructure bean = AzureInstanceInfrastructure.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(AzureInstanceInfrastructure bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    SettingAttribute hostConnectionAttr =
        settingsService.getSettingAttributeByName(accountId, yaml.getHostConnectionAttrsName());
    SettingAttribute winRmConnectionAttr =
        settingsService.getSettingAttributeByName(accountId, yaml.getWinRmConnectionAttributesName());
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setResourceGroup(yaml.getResourceGroup());
    bean.setSubscriptionId(yaml.getSubscriptionId());
    bean.setTags(yaml.getTags());
    if (hostConnectionAttr != null) {
      bean.setHostConnectionAttrs(hostConnectionAttr.getUuid());
    } else if (winRmConnectionAttr != null) {
      bean.setWinRmConnectionAttributes(winRmConnectionAttr.getUuid());
    } else {
      throw new WingsException(GENERAL_ERROR)
          .addParam("message",
              format("Connection "
                      + "Attribute with name %s does not exist",
                  isNotEmpty(yaml.getHostConnectionAttrsName()) ? yaml.getHostConnectionAttrsName()
                                                                : yaml.getWinRmConnectionAttributesName()));
    }
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
