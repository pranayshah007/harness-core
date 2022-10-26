/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.analysis.FeedbackPriority;
import software.wings.sm.StateType;
import software.wings.verification.datadog.DatadogLogCVConfigurationYaml;
import software.wings.verification.log.BugsnagCVConfigurationYaml;
import software.wings.verification.log.CustomLogCVServiceConfiguration.CustomLogsCVConfigurationYaml;
import software.wings.verification.log.ElkCVConfiguration.ElkCVConfigurationYaml;
import software.wings.verification.log.LogsCVConfiguration;
import software.wings.verification.log.LogsCVConfiguration.LogsCVConfigurationYaml;
import software.wings.verification.log.SplunkCVConfiguration.SplunkCVConfigurationYaml;
import software.wings.verification.log.StackdriverCVConfiguration.StackdriverCVConfigurationYaml;

import com.google.common.base.Preconditions;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogsCVConfigurationYamlHandler
    extends CVConfigurationYamlHandler<LogsCVConfigurationYaml, LogsCVConfiguration> {
  @Override
  public LogsCVConfigurationYaml toYaml(LogsCVConfiguration bean, String appId) {
    LogsCVConfigurationYaml yaml;
    switch (bean.getStateType()) {
      case SUMO:
        yaml = new LogsCVConfigurationYaml();
        break;
      case DATA_DOG_LOG:
        yaml = new DatadogLogCVConfigurationYaml();
        break;
      case ELK:
        yaml = new ElkCVConfigurationYaml();
        break;
      case BUG_SNAG:
        yaml = new BugsnagCVConfigurationYaml();
        break;
      case STACK_DRIVER_LOG:
        yaml = new StackdriverCVConfigurationYaml();
        break;
      case SPLUNKV2:
        yaml = new SplunkCVConfigurationYaml();
        break;
      case LOG_VERIFICATION:
        yaml = CustomLogsCVConfigurationYaml.builder().build();
        break;
      default:
        throw new IllegalStateException("Invalid state " + bean.getStateType());
    }

    super.toYaml(yaml, bean);
    yaml.setQuery(bean.getQuery());
    if (yaml.isEnabled24x7()) {
      yaml.setBaselineStartMinute(bean.getBaselineStartMinute());
      yaml.setBaselineEndMinute(bean.getBaselineEndMinute());
    }
    yaml.setType(bean.getStateType().name());
    yaml.setAlertPriority(bean.getAlertPriority().name());
    return yaml;
  }

  @Override
  public LogsCVConfiguration upsertFromYaml(
      ChangeContext<LogsCVConfigurationYaml> changeContext, List<ChangeContext> changeSetContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("Couldn't retrieve app from yaml:" + yamlFilePath, appId, USER);

    String envId = yamlHelper.getEnvironmentId(appId, yamlFilePath);

    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());

    CVConfiguration previous = cvConfigurationService.getConfiguration(name, appId, envId);

    LogsCVConfiguration bean = new LogsCVConfiguration();
    toBean(bean, changeContext, appId);
    saveToDatabase(bean, previous, appId);
    return bean;
  }

  protected void saveToDatabase(CVConfiguration bean, CVConfiguration previous, String appId) {
    if (previous != null) {
      bean.setUuid(previous.getUuid());
      cvConfigurationService.updateConfiguration(bean, appId);
    } else {
      bean.setUuid(generateUuid());
      cvConfigurationService.saveToDatabase(bean, true);
    }
  }

  @Override
  public Class getYamlClass() {
    return LogsCVConfigurationYaml.class;
  }

  @Override
  public LogsCVConfiguration get(String accountId, String yamlFilePath) {
    return (LogsCVConfiguration) yamlHelper.getCVConfiguration(accountId, yamlFilePath);
  }

  @SneakyThrows
  protected void toBean(LogsCVConfiguration bean, ChangeContext<LogsCVConfigurationYaml> changeContext, String appId) {
    LogsCVConfigurationYaml yaml = changeContext.getYaml();
    String yamlFilePath = changeContext.getChange().getFilePath();
    super.toBean(changeContext, bean, appId, yamlFilePath);
    bean.setQuery(yaml.getQuery());
    if (bean.isEnabled24x7()) {
      Preconditions.checkNotNull(
          yaml.getBaselineStartMinute(), "Baseline start minute not set for service guard config");
      Preconditions.checkNotNull(yaml.getBaselineEndMinute(), "Baseline end minute not set for service guard config");
    }
    if (yaml.getBaselineStartMinute() != null) {
      bean.setBaselineStartMinute(yaml.getBaselineStartMinute());
    }
    if (yaml.getBaselineEndMinute() != null) {
      bean.setBaselineEndMinute(yaml.getBaselineEndMinute());
    }
    bean.setStateType(StateType.valueOf(yaml.getType()));
    try {
      bean.setAlertPriority(FeedbackPriority.valueOf(yaml.getAlertPriority()));
    } catch (IllegalArgumentException exp) {
      throw new IllegalArgumentException(
          "Please enter valid Alert Priority value. List of valid values: P0, P1, P2, P3, P4, P5", exp);
    }
  }
}
