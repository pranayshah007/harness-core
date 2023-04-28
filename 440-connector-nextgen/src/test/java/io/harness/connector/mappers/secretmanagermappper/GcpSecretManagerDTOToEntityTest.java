/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermappper;

import static io.harness.rule.OwnerRule.RICHA;
import static io.harness.rule.OwnerRule.SHREYAS;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.gcpsecretmanager.GcpSecretManagerConnector;
import io.harness.connector.mappers.secretmanagermapper.GcpSecretManagerDTOToEntity;
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
public class GcpSecretManagerDTOToEntityTest extends CategoryTest {
  HashMap<String, Object> defaultFieldNamesToValue;

  @InjectMocks GcpSecretManagerDTOToEntity gcpSecretManagerDTOToEntity;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    defaultFieldNamesToValue = new HashMap<>();
    defaultFieldNamesToValue.put("isDefault", false);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultValueForIsDefaultAsFalse() {
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
    GcpSecretManagerConnector connector = gcpSecretManagerDTOToEntity.toConnectorEntity(connectorDTO);
    assertNotNull(connector);
    assertThat(connector.getIsDefault()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testIsDefaultValueWhenItsTrue() {
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
    connectorDTO.setDefault(true);
    GcpSecretManagerConnector connector = gcpSecretManagerDTOToEntity.toConnectorEntity(connectorDTO);
    assertNotNull(connector);
    assertThat(connector.getIsDefault()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testNonDefaultFieldsAreNull() throws IllegalAccessException {
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
    GcpSecretManagerConnector connector = gcpSecretManagerDTOToEntity.toConnectorEntity(connectorDTO);
    Field[] fields = GcpSecretManagerConnector.class.getDeclaredFields();
    for (Field field : fields) {
      if (!defaultFieldNamesToValue.containsKey(field.getName())) {
        field.setAccessible(true);
        Object value = field.get(connector);
        assertThat(value).isNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsAreNotNull() throws IllegalAccessException {
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
    GcpSecretManagerConnector connector = gcpSecretManagerDTOToEntity.toConnectorEntity(connectorDTO);
    Field[] fields = GcpSecretManagerConnector.class.getDeclaredFields();
    for (Field field : fields) {
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        field.setAccessible(true);
        Object value = field.get(connector);
        assertThat(value).isNotNull();
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testDefaultFieldsHaveCorrectValue() throws IllegalAccessException {
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
    GcpSecretManagerConnector connector = gcpSecretManagerDTOToEntity.toConnectorEntity(connectorDTO);
    Field[] fields = GcpSecretManagerConnector.class.getDeclaredFields();
    for (Field field : fields) {
      if (defaultFieldNamesToValue.containsKey(field.getName())) {
        field.setAccessible(true);
        Object value = field.get(connector);
        assertThat(value).isEqualTo(defaultFieldNamesToValue.get(field.getName()));
      }
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testAllFieldsHaveCorrectValueWithCredentials() throws IllegalAccessException {
    String secretRef = "secretRef";
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder()
                                                    .credentialsRef(SecretRefHelper.createSecretRef(secretRef))
                                                    .assumeCredentialsOnDelegate(false)
                                                    .isDefault(false)
                                                    .build();
    GcpSecretManagerConnector connector = gcpSecretManagerDTOToEntity.toConnectorEntity(connectorDTO);
    assertNotNull(connector);
    assertThat(connector.getIsDefault()).isEqualTo(false);
    assertThat(connector.getAssumeCredentialsOnDelegate()).isEqualTo(false);
    assertThat(connector.getCredentialsRef()).isEqualTo(secretRef);
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void testAllFieldsHaveCorrectValueWithCredentialsOnDelegate() throws IllegalAccessException {
    GcpSecretManagerConnectorDTO connectorDTO =
        GcpSecretManagerConnectorDTO.builder().assumeCredentialsOnDelegate(true).isDefault(false).build();
    GcpSecretManagerConnector connector = gcpSecretManagerDTOToEntity.toConnectorEntity(connectorDTO);
    assertNotNull(connector);
    assertThat(connector.getIsDefault()).isEqualTo(false);
    assertThat(connector.getAssumeCredentialsOnDelegate()).isEqualTo(true);
  }
}
