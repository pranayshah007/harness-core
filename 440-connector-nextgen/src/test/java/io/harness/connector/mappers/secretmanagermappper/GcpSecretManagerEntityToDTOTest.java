/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermappper;

import static io.harness.rule.OwnerRule.SHREYAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.gcpsecretmanager.GcpSecretManagerConnector;
import io.harness.connector.mappers.secretmanagermapper.GcpSecretManagerEntityToDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class GcpSecretManagerEntityToDTOTest extends CategoryTest {
  HashMap<String, Object> defaultFieldNamesToValue;
  @InjectMocks GcpSecretManagerEntityToDTO gcpSecretManagerEntityToDTO;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    defaultFieldNamesToValue = new HashMap<>();
    defaultFieldNamesToValue.put("isDefault", false);
    defaultFieldNamesToValue.put("credentials", SecretRefData.builder().build());
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testNonDefaultFieldsAreNull() throws IllegalAccessException {
    // Create connector entity.
    GcpSecretManagerConnector connector = GcpSecretManagerConnector.builder().build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    // Get all the fields in it
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out non default fields
      if (!defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the connector dto
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
    // Create connector entity.
    GcpSecretManagerConnector connector = GcpSecretManagerConnector.builder().build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    // Get all the fields in it
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out default fields
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the connector dto
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
    // Create connector entity.
    GcpSecretManagerConnector connector = GcpSecretManagerConnector.builder().build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    // Get all the fields in it
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    // Loop over all fields
    for (Field field : fields) {
      // Filter out default fields
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        // Set their accessibility as true
        field.setAccessible(true);
        // Get its value in the connector dto
        Object value = field.get(connectorDTO);
        // asset that default value is same as that defined in map created at test setup.
        assertThat(value).isEqualTo(defaultFieldNamesToValue.get(field.getName()));
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testEntityToDTOWithValues() throws IllegalAccessException {
    // Create connector entity.
    GcpSecretManagerConnector connector =
        GcpSecretManagerConnector.builder().credentialsRef("credential-ref").isDefault(false).build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    // Get all the fields in it
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    // Loop over all fields
    assertThat(fields.length).isEqualTo(3);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getDelegateSelectors()).isNull();
    assertThat(connectorDTO.getCredentialsRef()).isEqualTo(SecretRefHelper.createSecretRef("credential-ref"));
    assertThat(connectorDTO.isDefault()).isFalse();
  }
}
