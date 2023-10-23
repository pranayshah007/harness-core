/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import software.wings.beans.yaml.ChangeContext;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.dynatrace.DynaTraceApplication;
import software.wings.service.intfc.dynatrace.DynaTraceService;
import software.wings.sm.StateType;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration.DynaTraceCVConfigurationYaml;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynatraceCVConfigurationYamlHandler
    extends CVConfigurationYamlHandler<DynaTraceCVConfigurationYaml, DynaTraceCVServiceConfiguration> {
  @Inject DynaTraceService dynaTraceService;
  @Override
  public DynaTraceCVConfigurationYaml toYaml(DynaTraceCVServiceConfiguration bean, String appId) {
    DynaTraceCVConfigurationYaml yaml = DynaTraceCVConfigurationYaml.builder().build();
    super.toYaml(yaml, bean);
    List<DynaTraceApplication> serviceList = dynaTraceService.getServices(bean.getConnectorId(), true);
    yaml.setDynatraceServiceName("");
    yaml.setDynatraceServiceEntityId("");
    if (isEmpty(serviceList)) {
      log.info("No dynatrace services found for the ConnectorDisconnectHandler " + bean.getConnectorId());
    } else {
      if (isNotEmpty(bean.getServiceEntityId())) {
        for (DynaTraceApplication service : serviceList) {
          if (service.getEntityId().equals(bean.getServiceEntityId())) {
            yaml.setDynatraceServiceName(service.getDisplayName());
            yaml.setDynatraceServiceEntityId(service.getEntityId());
            break;
          }
        }
        if (isEmpty(yaml.getDynatraceServiceName())) {
          log.info("No dynatrace service found for the serviceID " + bean.getServiceEntityId());
        }
      }
    }

    yaml.setType(StateType.DYNA_TRACE.name());
    return yaml;
  }

  @Override
  public DynaTraceCVServiceConfiguration upsertFromYaml(
      ChangeContext<DynaTraceCVConfigurationYaml> changeContext, List<ChangeContext> changeSetContext) {
    DynaTraceCVConfigurationYaml yaml = changeContext.getYaml();
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);

    notNullCheck("Couldn't retrieve app from yaml:" + yamlFilePath, appId, USER);

    String envId = yamlHelper.getEnvironmentId(appId, yamlFilePath);
    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());

    CVConfiguration previous = cvConfigurationService.getConfiguration(name, appId, envId);

    DynaTraceCVServiceConfiguration bean = DynaTraceCVServiceConfiguration.builder().build();
    super.toBean(changeContext, bean, appId, yamlFilePath);
    bean.setStateType(StateType.DYNA_TRACE);
    List<DynaTraceApplication> serviceList = dynaTraceService.getServices(bean.getConnectorId(), true);
    if (isEmpty(serviceList)) {
      throw new DataCollectionException("No dynatrace services found for the ConnectorDisconnectHandler " + bean.getConnectorName());
    }

    for (DynaTraceApplication service : serviceList) {
      if (service.getDisplayName().equals(yaml.getDynatraceServiceName().trim())) {
        if (isEmpty(yaml.getDynatraceServiceEntityId())
            || service.getEntityId().equals(yaml.getDynatraceServiceEntityId().trim())) {
          bean.setServiceEntityId(service.getEntityId());
          break;
        }
      }
    }

    if (isEmpty(bean.getServiceEntityId())) {
      throw new DataCollectionException(
          "No dynatrace service found for the service name " + yaml.getDynatraceServiceName());
    }

    if (previous != null) {
      bean.setUuid(previous.getUuid());
      cvConfigurationService.updateConfiguration(bean, appId);
    } else {
      bean.setUuid(generateUuid());
      cvConfigurationService.saveToDatabase(bean, true);
    }

    return bean;
  }

  @Override
  public Class getYamlClass() {
    return DynaTraceCVConfigurationYaml.class;
  }

  @Override
  public DynaTraceCVServiceConfiguration get(String accountId, String yamlFilePath) {
    return (DynaTraceCVServiceConfiguration) yamlHelper.getCVConfiguration(accountId, yamlFilePath);
  }
}
