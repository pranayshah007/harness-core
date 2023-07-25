/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.servicenow.utils;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ServiceNowException;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowFieldTypeNG;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class ServiceNowFieldNGUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testParseServiceNowFieldNG() throws IOException {
    // unknown type field

    ServiceNowFieldNG serviceNowFieldNG =
        ServiceNowFieldNGUtils.parseServiceNowFieldNG(readResource("servicenow/utils/unknownTypeField.json"));

    assertThat(serviceNowFieldNG.getKey()).isEqualTo("parent");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("Parent");
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.UNKNOWN);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("");

    // choice based fields
    serviceNowFieldNG =
        ServiceNowFieldNGUtils.parseServiceNowFieldNG(readResource("servicenow/utils/choiceBasedField.json"));
    assertThat(serviceNowFieldNG.getKey()).isEqualTo("priority");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("Priority");
    assertThat(serviceNowFieldNG.getAllowedValues()).hasSize(6);
    assertThat(serviceNowFieldNG.getSchema().isArray()).isTrue();
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.OPTION);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("integer");

    // mandatory and unknown type field
    serviceNowFieldNG = ServiceNowFieldNGUtils.parseServiceNowFieldNG(
        readResource("servicenow/utils/mandatoryAndUnknownTypeField.json"));
    assertThat(serviceNowFieldNG.getKey()).isEqualTo("sys_id");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("Sys ID");
    assertThat(serviceNowFieldNG.isRequired()).isTrue();
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.UNKNOWN);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("");

    // multitext and unknown type field
    serviceNowFieldNG = ServiceNowFieldNGUtils.parseServiceNowFieldNG(
        readResource("servicenow/utils/multiTextAndUnknownTypeField.json"));
    assertThat(serviceNowFieldNG.getKey()).isEqualTo("comments_and_work_notes");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("Comments and Work notes");
    assertThat(serviceNowFieldNG.getSchema().isMultilineText()).isTrue();
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.UNKNOWN);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("");

    // custom  and string type field
    serviceNowFieldNG =
        ServiceNowFieldNGUtils.parseServiceNowFieldNG(readResource("servicenow/utils/customAndStringTypeField.json"));
    assertThat(serviceNowFieldNG.getKey()).isEqualTo("u_string_1");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("testNametestName");
    assertThat(serviceNowFieldNG.isCustom()).isTrue();
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.STRING);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("string");

    // integer field
    serviceNowFieldNG =
        ServiceNowFieldNGUtils.parseServiceNowFieldNG(readResource("servicenow/utils/integerField.json"));
    assertThat(serviceNowFieldNG.getKey()).isEqualTo("child_incidents");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("Child Incidents");
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.INTEGER);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("integer");

    // boolean field
    serviceNowFieldNG =
        ServiceNowFieldNGUtils.parseServiceNowFieldNG(readResource("servicenow/utils/booleanField.json"));
    assertThat(serviceNowFieldNG.getKey()).isEqualTo("knowledge");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("Knowledge");
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.BOOLEAN);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("boolean");

    // date time field
    serviceNowFieldNG =
        ServiceNowFieldNGUtils.parseServiceNowFieldNG(readResource("servicenow/utils/dateTimeField.json"));
    assertThat(serviceNowFieldNG.getKey()).isEqualTo("closed_at");
    assertThat(serviceNowFieldNG.getName()).isEqualTo("Closed");
    assertThat(serviceNowFieldNG.getSchema().getType()).isEqualTo(ServiceNowFieldTypeNG.DATE_TIME);
    assertThat(serviceNowFieldNG.getSchema().getTypeStr()).isEqualTo("glide_date_time");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testParseServiceNowFieldNGNegativeCases() {
    // error from parseServiceNowFieldNG : missing key

    assertThatThrownBy(
        () -> ServiceNowFieldNGUtils.parseServiceNowFieldNG(readResource("servicenow/utils/missingKeyField.json")))
        .isInstanceOf(ServiceNowException.class);

    // error from  parseServiceNowFieldSchemaNG : invalid multitext value

    assertThatThrownBy(()
                           -> ServiceNowFieldNGUtils.parseServiceNowFieldNG(
                               readResource("servicenow/utils/invalidMultiLineTextField.json")))
        .isInstanceOf(ServiceNowException.class)
        .getCause()
        .isInstanceOf(ServiceNowException.class);

    // error from  parseServiceNowFieldAllowedValueNG : invalid "value" in allowed value

    assertThatThrownBy(()
                           -> ServiceNowFieldNGUtils.parseServiceNowFieldNG(
                               readResource("servicenow/utils/invalidAllowedValuesField.json")))
        .isInstanceOf(ServiceNowException.class)
        .getCause()
        .isInstanceOf(ServiceNowException.class);
  }

  private JsonNode readResource(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL jsonFile = classLoader.getResource(filePath);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readTree(jsonFile);
  }
}
