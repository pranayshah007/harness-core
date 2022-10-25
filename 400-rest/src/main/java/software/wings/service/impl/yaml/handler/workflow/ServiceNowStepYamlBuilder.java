/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.servicenow.ServiceNowFields.CHANGE_REQUEST_NUMBER;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.serializer.JsonUtils;

import software.wings.beans.SettingAttribute;
import software.wings.beans.servicenow.ServiceNowCreateUpdateParams;
import software.wings.beans.servicenow.ServiceNowFields;
import software.wings.beans.yaml.ChangeContext;
import software.wings.delegatetasks.servicenow.ServiceNowAction;
import software.wings.exception.IncompleteStateException;
import software.wings.service.intfc.SettingsService;
import software.wings.yaml.workflow.StepYaml;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(CDC)
@Slf4j
public class ServiceNowStepYamlBuilder extends StepYamlBuilder {
  private static final String SERVICE_NOW_CREATE_UPDATE_PARAMS = "serviceNowCreateUpdateParams";
  private static final String SERVICE_NOW_ACTION = "action";
  private static final String SNOW_CONNECTOR_ID = "snowConnectorId";
  private static final String SNOW_CONNECTOR_NAME = "snowConnectorName";
  @Inject private SettingsService settingsService;

  @Override
  public void validate(ChangeContext<StepYaml> changeContext) {
    Map<String, Object> snowParams =
        (Map<String, Object>) changeContext.getYaml().getProperties().get("serviceNowCreateUpdateParams");
    validateParamsAreInPlace(snowParams);
    validateActionType(snowParams);
    ServiceNowCreateUpdateParams serviceNowCreateUpdateParams =
        JsonUtils.asObject(JsonUtils.asJson(snowParams), ServiceNowCreateUpdateParams.class);
    validateConnector(serviceNowCreateUpdateParams);
    validateStructureForGivenActionType(serviceNowCreateUpdateParams);
  }

  @Override
  public void convertIdToNameForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, Map<String, Object> inputProperties) {
    if (SERVICE_NOW_CREATE_UPDATE_PARAMS.equals(name)) {
      Map<String, Object> snowParams = (Map<String, Object>) objectValue;
      String snowConnectorId = (String) snowParams.get(SNOW_CONNECTOR_ID);
      SettingAttribute snowSettingAttribute = settingsService.get(snowConnectorId);
      notNullCheck("ServiceNow connector does not exist.", snowSettingAttribute);
      snowParams.remove(SNOW_CONNECTOR_ID);
      snowParams.put(SNOW_CONNECTOR_NAME, snowSettingAttribute.getName());
      outputProperties.put(SERVICE_NOW_CREATE_UPDATE_PARAMS, snowParams);
      return;
    }
    outputProperties.put(name, objectValue);
  }

  @Override
  public void convertNameToIdForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, String accountId, Map<String, Object> inputProperties) {
    if (SERVICE_NOW_CREATE_UPDATE_PARAMS.equals(name)) {
      Map<String, Object> snowParams = (Map<String, Object>) objectValue;
      if (snowParams.containsKey(SNOW_CONNECTOR_ID)) {
        log.info(YAML_ID_LOG, "SERVICENOW", accountId);
      }
      if (snowParams.containsKey(SNOW_CONNECTOR_NAME)) {
        String snowConnectorName = (String) snowParams.get(SNOW_CONNECTOR_NAME);
        SettingAttribute snowSettingAttribute = settingsService.getSettingAttributeByName(accountId, snowConnectorName);
        notNullCheck(String.format("ServiceNow connector %s does not exist.", snowConnectorName), snowSettingAttribute);
        snowParams.remove(SNOW_CONNECTOR_NAME);
        snowParams.put(SNOW_CONNECTOR_ID, snowSettingAttribute.getUuid());
      }
      outputProperties.put(SERVICE_NOW_CREATE_UPDATE_PARAMS, snowParams);
      return;
    }
    outputProperties.put(name, objectValue);
  }

  private void validateParamsAreInPlace(Map<String, Object> snowParams) {
    if (isEmpty(snowParams)) {
      throw new IncompleteStateException(
          "\"serviceNowCreateUpdateParams\" could not be empty. Please provide the values.");
    }
  }

  private void validateStructureForGivenActionType(ServiceNowCreateUpdateParams serviceNowCreateUpdateParams) {
    switch (serviceNowCreateUpdateParams.getAction()) {
      case CREATE:
        validateCreate(serviceNowCreateUpdateParams);
        break;
      case UPDATE:
        validateUpdate(serviceNowCreateUpdateParams);
        break;
      case IMPORT_SET:
        validateImportSet(serviceNowCreateUpdateParams);
        break;
      default:
        throw new IncompleteStateException("Provided serviceNowAction is not supported. ");
    }
  }

  private void validateConnector(ServiceNowCreateUpdateParams serviceNowCreateUpdateParams) {
    if (isBlank(serviceNowCreateUpdateParams.getSnowConnectorName())
        && isBlank(serviceNowCreateUpdateParams.getSnowConnectorId())) {
      throw new IncompleteStateException("\"snowConnectorName\" could not be empty or null.");
    }
  }

  private void validateImportSet(ServiceNowCreateUpdateParams serviceNowCreateUpdateParams) {
    if (isBlank(serviceNowCreateUpdateParams.getImportSetTableName())) {
      throw new IncompleteStateException("\"importSetTableName\" could not be empty or null.");
    }
    if (isBlank(serviceNowCreateUpdateParams.getJsonBody())) {
      throw new IncompleteStateException("\"jsonBody\" could not be empty or null.");
    }
  }

  private void validateUpdate(ServiceNowCreateUpdateParams serviceNowCreateUpdateParams) {
    if (isBlank(serviceNowCreateUpdateParams.getTicketType())) {
      throw new IncompleteStateException("\"ticketType\" could not be empty or null.");
    }
    if (isBlank(serviceNowCreateUpdateParams.getIssueNumber()) && !serviceNowCreateUpdateParams.isUpdateMultiple()) {
      throw new IncompleteStateException("\"issueNumber\" could not be empty or null.");
    }
    if (serviceNowCreateUpdateParams.isUpdateMultiple()) {
      if (!serviceNowCreateUpdateParams.fetchFields().containsKey(CHANGE_REQUEST_NUMBER)
          || isBlank(serviceNowCreateUpdateParams.fetchFields().get(CHANGE_REQUEST_NUMBER))) {
        throw new IncompleteStateException("\"CHANGE_REQUEST_NUMBER\" could not be empty or null.");
      }
    }
  }

  private void validateCreate(ServiceNowCreateUpdateParams serviceNowCreateUpdateParams) {
    if (isBlank(serviceNowCreateUpdateParams.getTicketType())) {
      throw new IncompleteStateException("\"ticketType\" could not be empty or null.");
    }
    if (isEmpty(serviceNowCreateUpdateParams.fetchFields())) {
      throw new IncompleteStateException("\"fields\" could not be empty or null.");
    }
    if (isBlank(serviceNowCreateUpdateParams.fetchFields().get(ServiceNowFields.DESCRIPTION))) {
      throw new IncompleteStateException("\"DESCRIPTION\" could not be empty or null.");
    }
    if (isBlank(serviceNowCreateUpdateParams.fetchFields().get(ServiceNowFields.SHORT_DESCRIPTION))) {
      throw new IncompleteStateException("\"SHORT_DESCRIPTION\" could not be empty or null.");
    }
  }

  private void validateActionType(Map<String, Object> snowParams) {
    String serviceNowAction = (String) snowParams.get(SERVICE_NOW_ACTION);
    if (isBlank(serviceNowAction)) {
      throw new IncompleteStateException(
          String.format("\"action\" could not be null or empty. Please, provide valid value: %s",
              Arrays.toString(ServiceNowAction.values())));
    }
    try {
      ServiceNowAction.valueOf(serviceNowAction);
    } catch (IllegalArgumentException e) {
      throw new IncompleteStateException(
          String.format("Invalid value %s provided for \"action\". Please, provide valid value: %s", serviceNowAction,
              Arrays.toString(ServiceNowAction.values())));
    }
  }
}
