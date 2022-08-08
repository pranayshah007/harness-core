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
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(CDP)
public class AwsInstanceInfrastructureYamlHandler
    extends CloudProviderInfrastructureYamlHandler<Yaml, AwsInstanceInfrastructure> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;
  @Override
  public Yaml toYaml(AwsInstanceInfrastructure bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    SettingAttribute hostNameConnectionAttr = settingsService.get(bean.getHostConnectionAttrs());
    return Yaml.builder()
        .autoScalingGroupName(bean.getAutoScalingGroupName())
        .awsInstanceFilter(bean.getAwsInstanceFilter())
        .desiredCapacity(bean.getDesiredCapacity())
        .hostNameConvention(bean.getHostNameConvention())
        .loadBalancerName(bean.getLoadBalancerId())
        .region(bean.getRegion())
        .cloudProviderName(cloudProvider.getName())
        .hostConnectionAttrsName(hostNameConnectionAttr.getName())
        .type(InfrastructureType.AWS_INSTANCE)
        .hostConnectionType(bean.getHostConnectionType())
        .expressions(bean.getExpressions())
        .useAutoScalingGroup(bean.isProvisionInstances())
        .build();
  }

  @Override
  public AwsInstanceInfrastructure upsertFromYaml(
      ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    AwsInstanceInfrastructure bean = AwsInstanceInfrastructure.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(AwsInstanceInfrastructure bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    SettingAttribute hostConnectionAttr =
        settingsService.getSettingAttributeByName(accountId, yaml.getHostConnectionAttrsName());
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    notNullCheck(format("Connection Attribute with name %s does not exist", yaml.getHostConnectionAttrsName()),
        hostConnectionAttr);
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setAutoScalingGroupName(yaml.getAutoScalingGroupName());
    bean.setAwsInstanceFilter(yaml.getAwsInstanceFilter());
    bean.setDesiredCapacity(yaml.getDesiredCapacity());
    bean.setHostConnectionAttrs(hostConnectionAttr.getUuid());
    bean.setHostNameConvention(yaml.getHostNameConvention());
    bean.setLoadBalancerName(yaml.getLoadBalancerName());
    bean.setLoadBalancerId(yaml.getLoadBalancerName());
    bean.setRegion(yaml.getRegion());
    bean.setExpressions(yaml.getExpressions());
    bean.setProvisionInstances(yaml.isUseAutoScalingGroup());
    bean.setHostConnectionType(yaml.getHostConnectionType());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
