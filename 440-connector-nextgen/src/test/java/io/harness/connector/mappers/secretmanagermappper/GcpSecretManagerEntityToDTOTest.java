/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermappper;

import static io.harness.rule.OwnerRule.RICHA;
import static io.harness.rule.OwnerRule.SHREYAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.gcpsecretmanager.GcpSecretManagerConnector;
import io.harness.connector.mappers.secretmanagermapper.GcpSecretManagerEntityToDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
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
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testNonDefaultFieldsAreNull() throws IllegalAccessException {
    GcpSecretManagerConnector connector = GcpSecretManagerConnector.builder().build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    for (Field field : fields) {
      if (!defaultFieldNamesToValue.containsKey(field.getName())) {
        field.setAccessible(true);
        Object value = field.get(connectorDTO);
        assertThat(value).isNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsAreNotNull() throws IllegalAccessException {
    GcpSecretManagerConnector connector = GcpSecretManagerConnector.builder().build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    for (Field field : fields) {
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        field.setAccessible(true);
        Object value = field.get(connectorDTO);
        assertThat(value).isNotNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsHaveCorrectValue() throws IllegalAccessException {
    GcpSecretManagerConnector connector = GcpSecretManagerConnector.builder().build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    for (Field field : fields) {
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        field.setAccessible(true);
        Object value = field.get(connectorDTO);
        assertThat(value).isEqualTo(defaultFieldNamesToValue.get(field.getName()));
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testEntityToDTOWithValuesWithCredentials() throws IllegalAccessException {
    String credentialsRef = "credential-ref";
    GcpSecretManagerConnector connector =
        GcpSecretManagerConnector.builder().credentialsRef(credentialsRef).isDefault(false).build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    assertThat(fields.length).isEqualTo(4);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getDelegateSelectors()).isNull();
    assertThat(connectorDTO.getCredentialsRef()).isEqualTo(SecretRefHelper.createSecretRef(credentialsRef));
    assertThat(connectorDTO.isDefault()).isFalse();
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void testEntityToDTOWithValuesWithCredentialsOnDelegate() throws IllegalAccessException {
    GcpSecretManagerConnector connector =
        GcpSecretManagerConnector.builder().assumeCredentialsOnDelegate(true).isDefault(false).build();
    GcpSecretManagerConnectorDTO connectorDTO = gcpSecretManagerEntityToDTO.createConnectorDTO(connector);
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    assertThat(fields.length).isEqualTo(4);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getDelegateSelectors()).isNull();
    assertThat(connectorDTO.getCredentialsRef()).isNull();
    assertThat(connectorDTO.getAssumeCredentialsOnDelegate()).isTrue();
    assertThat(connectorDTO.isDefault()).isFalse();
  }
}
