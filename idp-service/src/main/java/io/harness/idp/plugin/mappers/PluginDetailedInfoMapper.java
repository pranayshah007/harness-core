/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.plugin.beans.ExportsData;
import io.harness.idp.plugin.beans.PluginInfoEntity;
import io.harness.idp.plugin.enums.ExportType;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.ExportDetails;
import io.harness.spec.server.idp.v1.model.Exports;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class PluginDetailedInfoMapper {
  public PluginDetailedInfo toDTO(PluginInfoEntity pluginInfoEntity, AppConfig appConfig,
      List<BackstageEnvSecretVariable> backstageEnvSecretVariables, List<ProxyHostDetail> proxyHostDetails) {
    PluginDetailedInfo pluginDetailedInfo = new PluginDetailedInfo();
    boolean isConfigSaved = appConfig != null;
    boolean isEnabled = isConfigSaved && appConfig.isEnabled();
    pluginDetailedInfo.setPluginDetails(PluginInfoMapper.toDTO(pluginInfoEntity, isEnabled));
    String config;
    if (isEnabled) {
      config = appConfig.getConfigs();
    } else {
      config =
          (isConfigSaved && appConfig.getConfigs() != null) ? appConfig.getConfigs() : pluginInfoEntity.getConfig();
    }
    Exports exports = new Exports();
    exports.setCards(getExportTypeCount(pluginInfoEntity, ExportType.CARD));
    exports.setTabContents(getExportTypeCount(pluginInfoEntity, ExportType.TAB_CONTENT));
    exports.setPages(getExportTypeCount(pluginInfoEntity, ExportType.PAGE));
    exports.setDefaultEntityTypes(pluginInfoEntity.getExports().getDefaultEntityTypes());
    List<ExportDetails> exportDetailsList = new ArrayList<>();
    for (ExportsData.ExportDetails details : pluginInfoEntity.getExports().getExportDetails()) {
      ExportDetails exportDetails = new ExportDetails();
      exportDetails.setName(details.getName());
      exportDetails.setType(details.getType().toString());
      exportDetails.setAddByDefault(Boolean.valueOf(details.getAddByDefault()));
      exportDetails.setDefaultRoute(details.getDefaultRoute());
      exportDetails.setLayoutSchemaSpecs(details.getLayoutSchemaSpecs());
      exportDetailsList.add(exportDetails);
    }
    exports.setExportDetails(exportDetailsList);
    pluginDetailedInfo.setSaved(isConfigSaved);
    pluginDetailedInfo.setExports(exports);
    pluginDetailedInfo.setConfig(config);
    pluginDetailedInfo.setEnvVariables(backstageEnvSecretVariables);
    pluginDetailedInfo.setProxy(proxyHostDetails);
    return pluginDetailedInfo;
  }

  private int getExportTypeCount(PluginInfoEntity pluginInfoEntity, ExportType exportType) {
    return (int) pluginInfoEntity.getExports()
        .getExportDetails()
        .stream()
        .filter(exportDetails -> exportDetails.getType().equals(exportType))
        .count();
  }
}
