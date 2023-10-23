/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermappper;

import static io.harness.rule.OwnerRule.SHREYAS;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.customsecretmanager.CustomSecretManagerConnector;
import io.harness.connector.mappers.secretmanagermapper.CustomSecretManagerDTOToEntity;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class CustomSecretManagerDTOToEntityTest extends CategoryTest {
  HashMap<String, Object> defaultFieldNamesToValue;

  @InjectMocks CustomSecretManagerDTOToEntity customSecretManagerDTOtoEntity;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    defaultFieldNamesToValue = new HashMap<>();
    defaultFieldNamesToValue.put("isDefault", false);
    defaultFieldNamesToValue.put("onDelegate", false);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  // Test isDefault default value. Function name needs change here.
  public void testIsDefaultDefaultValue() {
    // Create Connector DTO
    CustomSecretManagerConnectorDTO connectorDTO = CustomSecretManagerConnectorDTO.builder().build();
    // Map it to corresponding ConnectorDisconnectHandler
    CustomSecretManagerConnector connector = customSecretManagerDTOtoEntity.toConnectorEntity(connectorDTO);
    // Check ConnectorDisconnectHandler is not null
    assertNotNull(connector);
    // Check default value of isDefault is false
    assertThat(connector.getIsDefault()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testIsDefaultValue() {
    // Create Connector DTO
    CustomSecretManagerConnectorDTO connectorDTO = CustomSecretManagerConnectorDTO.builder().build();
    // Set execute on delegate as false
    connectorDTO.setDefault(true);
    // Map it to corresponding ConnectorDisconnectHandler
    CustomSecretManagerConnector connector = customSecretManagerDTOtoEntity.toConnectorEntity(connectorDTO);
    // Check ConnectorDisconnectHandler is not null
    assertNotNull(connector);
    // Check default value of execute on delegate is false
    assertThat(connector.getIsDefault()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testNonDefaultFieldsAreNull() throws IllegalAccessException {
    // Create ConnectorDisconnectHandler dto.
    CustomSecretManagerConnectorDTO connectorDTO = CustomSecretManagerConnectorDTO.builder().build();
    CustomSecretManagerConnector connector = customSecretManagerDTOtoEntity.toConnectorEntity(connectorDTO);
    // Get all the fields in it
    Field[] fields = CustomSecretManagerConnector.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out non default fields
      if (!defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the ConnectorDisconnectHandler entity
        Object value = field.get(connector);
        // asset that the fields are null.
        assertThat(value).isNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsAreNotNull() throws IllegalAccessException {
    // Create ConnectorDisconnectHandler dto.
    CustomSecretManagerConnectorDTO connectorDTO = CustomSecretManagerConnectorDTO.builder().build();
    CustomSecretManagerConnector connector = customSecretManagerDTOtoEntity.toConnectorEntity(connectorDTO);
    // Get all the fields in it
    Field[] fields = CustomSecretManagerConnector.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out default fields
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the ConnectorDisconnectHandler entity
        Object value = field.get(connector);
        // asset that the fields are not null.
        assertThat(value).isNotNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsHaveCorrectValue() throws IllegalAccessException {
    // Create ConnectorDisconnectHandler dto.
    CustomSecretManagerConnectorDTO connectorDTO = CustomSecretManagerConnectorDTO.builder().build();
    CustomSecretManagerConnector connector = customSecretManagerDTOtoEntity.toConnectorEntity(connectorDTO);
    // Get all the fields in it
    Field[] fields = CustomSecretManagerConnector.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out default fields
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the ConnectorDisconnectHandler entity
        Object value = field.get(connector);
        // asset that default value is same as that defined in map created at test setup.
        assertThat(value).isEqualTo(defaultFieldNamesToValue.get(field.getName()));
      }
    }
  }
}
