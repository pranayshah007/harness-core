/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;

import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.verification.CVConfigurationService;

import com.google.inject.Inject;
import java.util.Date;

public abstract class CVConfigurationYamlHandler<Y extends CVConfigurationYaml, B extends CVConfiguration>
    extends BaseYamlHandler<Y, B> {
  @Inject YamlHelper yamlHelper;
  @Inject CVConfigurationService cvConfigurationService;
  @Inject SettingsService settingsService;
  @Inject AppService appService;
  @Inject EnvironmentService environmentService;
  @Inject ServiceResourceService serviceResourceService;

  public void toYaml(CVConfigurationYaml yaml, CVConfiguration bean) {
    yaml.setAnalysisTolerance(bean.getAnalysisTolerance());
    yaml.setConnectorName(settingsService.get(bean.getConnectorId()).getName());
    yaml.setEnabled24x7(bean.isEnabled24x7());
    yaml.setAlertEnabled(bean.isAlertEnabled());

    Application application = appService.get(bean.getAppId());
    Service service = serviceResourceService.getWithDetails(application.getUuid(), bean.getServiceId());
    yaml.setServiceName(service.getName());
    yaml.setAlertThreshold(bean.getAlertThreshold());
    yaml.setNumOfOccurrencesForAlert(bean.getNumOfOccurrencesForAlert());
    if (bean.getSnoozeStartTime() > 0) {
      yaml.setSnoozeStartTime(new Date(bean.getSnoozeStartTime()));
    }

    if (bean.getSnoozeEndTime() > 0) {
      yaml.setSnoozeEndTime(new Date(bean.getSnoozeEndTime()));
    }
  }

  @Override
  public void delete(ChangeContext<Y> changeContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    String envId = yamlHelper.getEnvironmentId(appId, yamlFilePath);
    String name = yamlHelper.getNameFromYamlFilePath(yamlFilePath);
    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(name, appId, envId);

    if (cvConfiguration != null) {
      cvConfigurationService.deleteConfiguration(
          accountId, appId, cvConfiguration.getUuid(), changeContext.getChange().isSyncFromGit());
    }
  }

  public void toBean(ChangeContext<Y> changeContext, B bean, String appId, String yamlPath) {
    Y yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());
    String envId = yamlHelper.getEnvironmentId(appId, yamlPath);
    bean.setAppId(appId);
    bean.setName(name);
    Service service = serviceResourceService.getServiceByName(appId, yaml.getServiceName());
    if (service == null) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Invalid Service name in Yaml for CVConfiguration.");
    }
    bean.setAccountId(accountId);
    bean.setEnvId(envId);
    bean.setEnabled24x7(yaml.isEnabled24x7());
    bean.setAnalysisTolerance(yaml.getAnalysisTolerance());
    bean.setServiceId(service.getUuid());
    if (yaml.getNumOfOccurrencesForAlert() > 4) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Maximum allowed value for NumOfOccurrencesForAlert is 4");
    }
    bean.setAlertThreshold(yaml.getAlertThreshold());
    bean.setNumOfOccurrencesForAlert(yaml.getNumOfOccurrencesForAlert());
    bean.setSyncFromGit(changeContext.getChange().isSyncFromGit());
    bean.setAlertEnabled(yaml.isAlertEnabled());
    if (yaml.getSnoozeStartTime() != null) {
      bean.setSnoozeStartTime(yaml.getSnoozeStartTime().getTime());
    }
    if (yaml.getSnoozeEndTime() != null) {
      bean.setSnoozeEndTime(yaml.getSnoozeEndTime().getTime());
    }
    SettingAttribute connector = getConnector(yaml, accountId);
    if (connector == null) {
      throw new WingsException("Invalid ConnectorDisconnectHandler name specified in yaml: " + yaml.getConnectorName());
    }
    bean.setConnectorId(connector.getUuid());
  }

  SettingAttribute getConnector(CVConfigurationYaml yaml, String accountId) {
    return settingsService.getSettingAttributeByName(accountId, yaml.getConnectorName());
  }

  protected CVConfiguration getPreviousCVConfiguration(ChangeContext<? extends CVConfigurationYaml> changeContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String appId = getAppId(changeContext);
    String envId = yamlHelper.getEnvironmentId(appId, yamlFilePath);
    String name = yamlHelper.getNameFromYamlFilePath(yamlFilePath);
    return cvConfigurationService.getConfiguration(name, appId, envId);
  }

  protected String getAppId(ChangeContext<? extends CVConfigurationYaml> changeContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);

    notNullCheck("Couldn't retrieve app from yaml:" + yamlFilePath, appId, USER);
    return appId;
  }
}
