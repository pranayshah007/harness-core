/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.beans.servicenow.ServiceNowCreateUpdateParams;
import software.wings.beans.servicenow.ServiceNowFields;
import software.wings.beans.servicenow.ServiceNowTicketType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(CDC)
public class ServiceNowStepYamlBuilderTest extends StepYamlBuilderTestBase {
  @InjectMocks private ServiceNowStepYamlBuilder serviceNowStepYamlBuilder;

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testNameToIdForKnownTypes() {
    Map<String, Object> inputProperties = getInputProperties(true);
    Map<String, Object> outputProperties = new HashMap<>();

    inputProperties.forEach((name, value)
                                -> serviceNowStepYamlBuilder.convertNameToIdForKnownTypes(
                                    name, value, outputProperties, APP_ID, ACCOUNT_ID, null));
    assertThat(outputProperties).containsExactlyInAnyOrderEntriesOf(getInputProperties(false));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testIdToNameForKnownTypes() {
    Map<String, Object> inputProperties = getInputProperties(false);
    Map<String, Object> outputProperties = new HashMap<>();

    inputProperties.forEach(
        (name, value)
            -> serviceNowStepYamlBuilder.convertIdToNameForKnownTypes(name, value, outputProperties, APP_ID, null));
    assertThat(outputProperties).containsExactlyInAnyOrderEntriesOf(getInputProperties(true));
  }

  private Map<String, Object> getInputProperties(boolean withName) {
    Map<String, Object> inputProperties = new HashMap<>();
    Map<String, Object> serviceNowCreateUpdateParams = getServiceNowCreateUpdateParams();
    if (withName) {
      serviceNowCreateUpdateParams.put(SNOW_CONNECTOR_NAME, SNOW_CONNECTOR_NAME);
      serviceNowCreateUpdateParams.remove(SNOW_CONNECTOR_ID);
    }
    inputProperties.put(SERVICE_NOW_CREATE_UPDATE_PARAMS, serviceNowCreateUpdateParams);
    inputProperties.put("templateUuid", null);
    inputProperties.put("templateVariables", null);
    inputProperties.put("templateVersion", null);
    return inputProperties;
  }

  private Map<String, Object> getServiceNowCreateUpdateParams() {
    ServiceNowCreateUpdateParams serviceNowCreateUpdateParams = new ServiceNowCreateUpdateParams();
    serviceNowCreateUpdateParams.setSnowConnectorId(SNOW_CONNECTOR_ID);
    serviceNowCreateUpdateParams.setTicketType(ServiceNowTicketType.INCIDENT.name());
    serviceNowCreateUpdateParams.setFields(Collections.singletonMap(ServiceNowFields.DESCRIPTION, "value"));
    serviceNowCreateUpdateParams.setIssueNumber("1234");
    return JsonUtils.asMap(JsonUtils.asJson(serviceNowCreateUpdateParams));
  }
}
