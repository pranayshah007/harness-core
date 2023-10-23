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
import io.harness.connector.mappers.secretmanagermapper.CustomSecretManagerEntityToDTO;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class CustomSecretManagerEntityToDTOTest extends CategoryTest {
  HashMap<String, Object> defaultFieldNamesToValue;
  @InjectMocks CustomSecretManagerEntityToDTO customSecretManagerEntitytoDTO;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    defaultFieldNamesToValue = new HashMap<>();
    defaultFieldNamesToValue.put("isDefault", false);
    defaultFieldNamesToValue.put("onDelegate", false);
    defaultFieldNamesToValue.put("harnessManaged", false);
    // Default of ConnectorDisconnectHandler ref is secret ref data object which has all fields as null.
    defaultFieldNamesToValue.put("connectorRef", new SecretRefData());
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testExecuteOnDelegateDefaultValue() {
    // Create ConnectorDisconnectHandler entity.
    CustomSecretManagerConnector customSecretManagerConnector = CustomSecretManagerConnector.builder().build();
    // Map it to corresponding DTO
    CustomSecretManagerConnectorDTO customSecretManagerConnectorDTO =
        customSecretManagerEntitytoDTO.createConnectorDTO(customSecretManagerConnector);
    // Check
    assertNotNull(customSecretManagerConnectorDTO);
    assertThat(customSecretManagerConnectorDTO.getOnDelegate()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testExecuteOnDelegateFalseValue() {
    // Create ConnectorDisconnectHandler entity.
    CustomSecretManagerConnector customSecretManagerConnector = CustomSecretManagerConnector.builder().build();
    customSecretManagerConnector.setExecuteOnDelegate(false);
    // Map it to corresponding DTO
    CustomSecretManagerConnectorDTO customSecretManagerConnectorDTO =
        customSecretManagerEntitytoDTO.createConnectorDTO(customSecretManagerConnector);
    // Check
    assertNotNull(customSecretManagerConnectorDTO);
    assertThat(customSecretManagerConnectorDTO.getOnDelegate()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testNonDefaultFieldsAreNull() throws IllegalAccessException {
    // Create ConnectorDisconnectHandler entity.
    CustomSecretManagerConnector connector = CustomSecretManagerConnector.builder().build();
    CustomSecretManagerConnectorDTO connectorDTO = customSecretManagerEntitytoDTO.createConnectorDTO(connector);
    // Get all the fields in it
    Field[] fields = CustomSecretManagerConnectorDTO.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out non default fields
      if (!defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the ConnectorDisconnectHandler dto
        Object value = field.get(connectorDTO);
        // asset that the fields are null.
        assertThat(value).isNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsAreNotNull() throws IllegalAccessException {
    // Create ConnectorDisconnectHandler entity.
    CustomSecretManagerConnector connector = CustomSecretManagerConnector.builder().build();
    CustomSecretManagerConnectorDTO connectorDTO = customSecretManagerEntitytoDTO.createConnectorDTO(connector);
    // Get all the fields in it
    Field[] fields = CustomSecretManagerConnectorDTO.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out default fields
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the ConnectorDisconnectHandler dto
        Object value = field.get(connectorDTO);
        // asset that the fields are not null.
        assertThat(value).isNotNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsHaveCorrectValue() throws IllegalAccessException {
    // Create ConnectorDisconnectHandler entity.
    CustomSecretManagerConnector connector = CustomSecretManagerConnector.builder().build();
    CustomSecretManagerConnectorDTO connectorDTO = customSecretManagerEntitytoDTO.createConnectorDTO(connector);
    // Get all the fields in it
    Field[] fields = CustomSecretManagerConnectorDTO.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out default fields
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the ConnectorDisconnectHandler dto
        Object value = field.get(connectorDTO);
        // asset that default value is same as that defined in map created at test setup.
        assertThat(value).isEqualTo(defaultFieldNamesToValue.get(field.getName()));
      }
    }
  }
}
