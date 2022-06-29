/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.InfraDefinition;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.InfrastructureType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsEcsInfrastructure.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CDP)
public class AwsEcsInfrastructureYamlHandler
    extends CloudProviderInfrastructureYamlHandler<Yaml, AwsEcsInfrastructure> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;

  @Override
  public Yaml toYaml(AwsEcsInfrastructure bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    return Yaml.builder()
        .assignPublicIp(bean.isAssignPublicIp())
        .executionRole(bean.getExecutionRole())
        .launchType(bean.getLaunchType())
        .region(bean.getRegion())
        .securityGroupIds(bean.getSecurityGroupIds())
        .subnetIds(bean.getSubnetIds())
        .vpcId(bean.getVpcId())
        .type(InfrastructureType.AWS_ECS)
        .cloudProviderName(cloudProvider.getName())
        .expressions(bean.getExpressions())
        .clusterName(bean.getClusterName())
        .build();
  }

  @Override
  public AwsEcsInfrastructure upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    AwsEcsInfrastructure bean = AwsEcsInfrastructure.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(AwsEcsInfrastructure bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setAssignPublicIp(yaml.isAssignPublicIp());
    bean.setExecutionRole(yaml.getExecutionRole());
    bean.setLaunchType(yaml.getLaunchType());
    bean.setRegion(yaml.getRegion());
    bean.setSecurityGroupIds(yaml.getSecurityGroupIds());
    bean.setSubnetIds(yaml.getSubnetIds());
    bean.setVpcId(yaml.getVpcId());
    bean.setExpressions(yaml.getExpressions());
    bean.setClusterName(yaml.getClusterName());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
