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
import software.wings.infra.PcfInfraStructure;
import software.wings.infra.PcfInfraStructure.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CDP)
public class PcfInfraStructureYamlHandler extends CloudProviderInfrastructureYamlHandler<Yaml, PcfInfraStructure> {
  @Inject private SettingsService settingsService;
  @Inject private YamlHelper yamlHelper;

  @Override
  public Yaml toYaml(PcfInfraStructure bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    return Yaml.builder()
        .organization(bean.getOrganization())
        .routeMaps(bean.getRouteMaps())
        .space(bean.getSpace())
        .tempRouteMap(bean.getTempRouteMap())
        .cloudProviderName(cloudProvider.getName())
        .type(InfrastructureType.PCF_INFRASTRUCTURE)
        .build();
  }

  @Override
  public PcfInfraStructure upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    PcfInfraStructure bean = PcfInfraStructure.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(PcfInfraStructure bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, changeContext.getChange().getFilePath());
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    settingsService.checkRbacOnSettingAttribute(appId, cloudProvider);
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setOrganization(yaml.getOrganization());
    bean.setRouteMaps(yaml.getRouteMaps());
    bean.setSpace(yaml.getSpace());
    bean.setTempRouteMap(yaml.getTempRouteMap());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
