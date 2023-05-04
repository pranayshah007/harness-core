/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpsecretmanager;

import static io.harness.rule.OwnerRule.SHREYAS;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class GcpSecretManagerConnectorDTOTest extends CategoryTest {
  HashMap<String, Object> defaultFieldNamesToValue;

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
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
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
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
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
    GcpSecretManagerConnectorDTO connectorDTO = GcpSecretManagerConnectorDTO.builder().build();
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
  public void testInputFieldsHaveCorrectValue() throws IllegalAccessException {
    String secretRef = randomAlphabetic(10);
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretRef);
    GcpSecretManagerConnectorDTO connectorDTO =
        GcpSecretManagerConnectorDTO.builder().credentialsRef(secretRefData).isDefault(false).build();
    Field[] fields = GcpSecretManagerConnectorDTO.class.getDeclaredFields();
    assertThat(fields.length).isEqualTo(4);
    assertThat(connectorDTO).isNotNull();
    assertThat(SecretRefHelper.getSecretConfigString(connectorDTO.getCredentialsRef())).isEqualTo(secretRef);
    assertThat(connectorDTO.getDelegateSelectors()).isNull();
    assertThat(connectorDTO.isDefault()).isFalse();
  }
}
