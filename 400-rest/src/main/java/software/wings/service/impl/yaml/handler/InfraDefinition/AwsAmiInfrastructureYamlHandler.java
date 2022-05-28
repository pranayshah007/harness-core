/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.InfraDefinition;

import static io.harness.annotations.dev.HarnessModule._955_CG_YAML;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.AmiDeploymentType;
import software.wings.beans.InfrastructureType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsAmiInfrastructure.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CDP)
@TargetModule(_955_CG_YAML)
public class AwsAmiInfrastructureYamlHandler
    extends CloudProviderInfrastructureYamlHandler<Yaml, AwsAmiInfrastructure> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;

  @Override
  public Yaml toYaml(AwsAmiInfrastructure bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    String spotinstCloudProviderName = null;
    if (AmiDeploymentType.SPOTINST == bean.getAmiDeploymentType()) {
      SettingAttribute spotinstCloudProvider = settingsService.get(bean.getSpotinstCloudProvider());
      notNullCheck(
          "SettingAttribute can't be found for Id:" + bean.getSpotinstCloudProvider(), spotinstCloudProvider, USER);
      spotinstCloudProviderName = spotinstCloudProvider.getName();
    }

    return Yaml.builder()
        .autoScalingGroupName(bean.getAutoScalingGroupName())
        .classicLoadBalancers(bean.getClassicLoadBalancers())
        .hostNameConvention(bean.getHostNameConvention())
        .region(bean.getRegion())
        .stageClassicLoadBalancers(bean.getStageClassicLoadBalancers())
        .stageTargetGroupArns(bean.getStageTargetGroupArns())
        .targetGroupArns(bean.getTargetGroupArns())
        .cloudProviderName(cloudProvider.getName())
        .amiDeploymentType(bean.getAmiDeploymentType())
        .spotinstCloudProviderName(spotinstCloudProviderName)
        .spotinstElastiGroupJson(bean.getSpotinstElastiGroupJson())
        .asgIdentifiesWorkload(bean.isAsgIdentifiesWorkload())
        .useTrafficShift(bean.isUseTrafficShift())
        .type(InfrastructureType.AWS_AMI)
        .expressions(bean.getExpressions())
        .build();
  }

  @Override
  public AwsAmiInfrastructure upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    AwsAmiInfrastructure bean = AwsAmiInfrastructure.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(AwsAmiInfrastructure bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    String spotinstCloudProviderId = null;
    if (AmiDeploymentType.SPOTINST == yaml.getAmiDeploymentType()) {
      SettingAttribute spotinstCloudProvider =
          settingsService.getSettingAttributeByName(accountId, yaml.getSpotinstCloudProviderName());
      notNullCheck("SettingAttribute can't be found for Name:" + yaml.getSpotinstCloudProviderName(),
          spotinstCloudProvider, USER);
      spotinstCloudProviderId = spotinstCloudProvider.getUuid();
    }
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setRegion(yaml.getRegion());
    bean.setAutoScalingGroupName(yaml.getAutoScalingGroupName());
    bean.setClassicLoadBalancers(yaml.getClassicLoadBalancers());
    bean.setTargetGroupArns(yaml.getTargetGroupArns());
    bean.setHostNameConvention(yaml.getHostNameConvention());
    bean.setStageClassicLoadBalancers(yaml.getStageClassicLoadBalancers());
    bean.setStageTargetGroupArns(yaml.getStageTargetGroupArns());
    bean.setAmiDeploymentType(yaml.getAmiDeploymentType());
    bean.setSpotinstCloudProvider(spotinstCloudProviderId);
    bean.setSpotinstElastiGroupJson(yaml.getSpotinstElastiGroupJson());
    bean.setAsgIdentifiesWorkload(yaml.isAsgIdentifiesWorkload());
    bean.setUseTrafficShift(yaml.isUseTrafficShift());
    bean.setExpressions(yaml.getExpressions());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
