/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting.artifactserver;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.exception.HarnessException;

import software.wings.beans.SettingAttribute;
import software.wings.beans.settings.helm.OciHelmRepoConfig;
import software.wings.beans.settings.helm.OciHelmRepoConfig.Yaml;
import software.wings.beans.yaml.ChangeContext;

import java.util.List;

public class OciHelmRepoConfigYamlHandler extends HelmRepoYamlHandler<OciHelmRepoConfig.Yaml, OciHelmRepoConfig> {
  @Override
  public OciHelmRepoConfig.Yaml toYaml(SettingAttribute settingAttribute, String appId) {
    OciHelmRepoConfig ociHelmRepoConfig = (OciHelmRepoConfig) settingAttribute.getValue();

    OciHelmRepoConfig.Yaml yaml = OciHelmRepoConfig.Yaml.builder()
                                      .harnessApiVersion(getHarnessApiVersion())
                                      .type(ociHelmRepoConfig.getType())
                                      .url(ociHelmRepoConfig.getChartRepoUrl())
                                      .build();

    if (isNotBlank(ociHelmRepoConfig.getUsername())) {
      yaml.setUsername(ociHelmRepoConfig.getUsername());
      yaml.setPassword(getEncryptedYamlRef(ociHelmRepoConfig.getAccountId(), ociHelmRepoConfig.getEncryptedPassword()));
    }

    toYaml(yaml, settingAttribute, appId);

    return yaml;
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  protected SettingAttribute toBean(SettingAttribute previous, ChangeContext<OciHelmRepoConfig.Yaml> changeContext,
      List<ChangeContext> changeSetContext) throws HarnessException {
    String uuid = previous != null ? previous.getUuid() : null;
    OciHelmRepoConfig.Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();

    OciHelmRepoConfig ociHelmRepoConfig = OciHelmRepoConfig.builder()
                                              .accountId(accountId)
                                              .chartRepoUrl(yaml.getUrl())
                                              .username(yaml.getUsername())
                                              .encryptedPassword(yaml.getPassword())
                                              .build();

    return buildSettingAttribute(accountId, changeContext.getChange().getFilePath(), uuid, ociHelmRepoConfig);
  }
}
