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
import software.wings.infra.CodeDeployInfrastructure;
import software.wings.infra.CodeDeployInfrastructure.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class CodeDeployInfrastructureYamlHandler
    extends CloudProviderInfrastructureYamlHandler<Yaml, CodeDeployInfrastructure> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;

  @Override
  public Yaml toYaml(CodeDeployInfrastructure bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    return Yaml.builder()
        .region(bean.getRegion())
        .applicationName(bean.getApplicationName())
        .deploymentConfig(bean.getDeploymentConfig())
        .deploymentGroup(bean.getDeploymentGroup())
        .hostNameConvention(bean.getHostNameConvention())
        .cloudProviderName(cloudProvider.getName())
        .type(InfrastructureType.CODE_DEPLOY)
        .build();
  }

  @Override
  public CodeDeployInfrastructure upsertFromYaml(
      ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    CodeDeployInfrastructure bean = CodeDeployInfrastructure.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(CodeDeployInfrastructure bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setApplicationName(yaml.getApplicationName());
    bean.setDeploymentConfig(yaml.getDeploymentConfig());
    bean.setDeploymentGroup(yaml.getDeploymentGroup());
    bean.setHostNameConvention(yaml.getHostNameConvention());
    bean.setRegion(yaml.getRegion());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
