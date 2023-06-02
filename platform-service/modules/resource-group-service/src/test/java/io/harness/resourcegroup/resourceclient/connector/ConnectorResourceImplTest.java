/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.connector;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.UnitTests;
import io.harness.resourcegroup.v2.model.AttributeFilter;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class ConnectorResourceImplTest {
  @Inject @InjectMocks ConnectorResourceImpl connectorResource;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testIsValidAttributeFilter() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> connectorResource.isValidAttributeFilter(null));
    AttributeFilter attributeFilter = AttributeFilter.builder().attributeName("category").build();
    assertThat(connectorResource.isValidAttributeFilter(attributeFilter)).isEqualTo(false);
    attributeFilter = AttributeFilter.builder().build();
    assertThat(connectorResource.isValidAttributeFilter(attributeFilter)).isEqualTo(false);
    attributeFilter = AttributeFilter.builder().attributeName("category").attributeValues(List.of("test")).build();
    assertThat(connectorResource.isValidAttributeFilter(attributeFilter)).isEqualTo(true);
  }
}
