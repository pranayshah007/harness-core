/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting.verificationprovider;

import software.wings.beans.PrometheusConfig;
import software.wings.beans.PrometheusConfig.PrometheusYaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;

import java.util.List;

/**
 * Created by rsingh on 2/12/18.
 */
public class PrometheusConfigYamlHandler extends VerificationProviderYamlHandler<PrometheusYaml, PrometheusConfig> {
  @Override
  public PrometheusYaml toYaml(SettingAttribute settingAttribute, String appId) {
    PrometheusConfig config = (PrometheusConfig) settingAttribute.getValue();

    PrometheusYaml yaml = PrometheusYaml.builder()
                              .harnessApiVersion(getHarnessApiVersion())
                              .type(config.getType())
                              .prometheusUrl(config.getUrl())
                              .username(config.getUsername())
                              .password(getEncryptedYamlRef(config.getAccountId(), config.getEncryptedPassword()))
                              .build();
    toYaml(yaml, settingAttribute, appId);
    return yaml;
  }

  @Override
  protected SettingAttribute toBean(
      SettingAttribute previous, ChangeContext<PrometheusYaml> changeContext, List<ChangeContext> changeSetContext) {
    String uuid = previous != null ? previous.getUuid() : null;
    PrometheusYaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();

    PrometheusConfig config = PrometheusConfig.builder()
                                  .accountId(accountId)
                                  .url(yaml.getPrometheusUrl())
                                  .username(yaml.getUsername())
                                  .encryptedPassword(yaml.getPassword())
                                  .build();

    return buildSettingAttribute(accountId, changeContext.getChange().getFilePath(), uuid, config);
  }

  @Override
  public Class getYamlClass() {
    return PrometheusYaml.class;
  }
}
