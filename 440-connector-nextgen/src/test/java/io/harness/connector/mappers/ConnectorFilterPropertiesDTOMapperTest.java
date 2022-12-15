/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.entities.ConnectorFilterProperties;
import io.harness.connector.mappers.filter.ConnectorFilterPropertiesMapper;
import io.harness.connector.utils.ConnectorFilterTestHelper;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Arrays;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ConnectorFilterPropertiesDTOMapperTest extends CategoryTest {
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String filterIdentifier = "filterIdentifier";
  @InjectMocks ConnectorFilterPropertiesMapper connectorFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toConnectorFilter() {
    ConnectorFilterPropertiesDTO connectorFilter =
        ConnectorFilterTestHelper.createConnectorFilterPropertiesDTOForTest();
    FilterPropertiesDTO filterPropertiesDTO = (FilterPropertiesDTO) connectorFilter;
    filterPropertiesDTO.setTags(new HashMap<String, String>() {
      { put("key1", "value1"); }
    });
    ConnectorFilterProperties connectorFilterProperties =
        (ConnectorFilterProperties) connectorFilterPropertiesMapper.toEntity(connectorFilter);
    assertThat(connectorFilterProperties).isNotNull();
    assertThat(connectorFilterProperties.getInheritingCredentialsFromDelegate())
        .isEqualTo(connectorFilter.getInheritingCredentialsFromDelegate());
    assertThat(connectorFilterProperties.getConnectivityStatuses())
        .isEqualTo(connectorFilter.getConnectivityStatuses());
    assertThat(connectorFilterProperties.getTypes()).isEqualTo(connectorFilter.getTypes());
    assertThat(connectorFilterProperties.getConnectorNames()).isEqualTo(connectorFilter.getConnectorNames());
    assertThat(connectorFilterProperties.getCategories()).isEqualTo(connectorFilter.getCategories());
    assertThat(connectorFilterProperties.getConnectorIdentifiers())
        .isEqualTo(connectorFilter.getConnectorIdentifiers());
    assertThat(connectorFilterProperties.getDescription()).isEqualTo(connectorFilter.getDescription());
    assertThat(connectorFilterProperties.getTags().size()).isEqualTo(1);
    assertThat(connectorFilterProperties.getConnectorConnectivityModes())
        .isEqualTo(connectorFilter.getConnectorConnectivityModes());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toConnectorFilterDTO() {
    ConnectorFilterProperties connectorFilter =
        ConnectorFilterTestHelper.createConnectorFilterPropertiesEntityForTest();
    FilterProperties filterProperties = (FilterProperties) connectorFilter;
    filterProperties.setTags(Arrays.asList(
        NGTag.builder().key("tag").value("value1").build(), NGTag.builder().key("tag1").value("value").build()));
    ConnectorFilterPropertiesDTO connectorFilterProperties =
        (ConnectorFilterPropertiesDTO) connectorFilterPropertiesMapper.writeDTO(connectorFilter);
    assertThat(connectorFilterProperties).isNotNull();
    assertThat(connectorFilterProperties.getInheritingCredentialsFromDelegate())
        .isEqualTo(connectorFilter.getInheritingCredentialsFromDelegate());
    assertThat(connectorFilterProperties.getConnectivityStatuses())
        .isEqualTo(connectorFilter.getConnectivityStatuses());
    assertThat(connectorFilterProperties.getTypes()).isEqualTo(connectorFilter.getTypes());
    assertThat(connectorFilterProperties.getConnectorNames()).isEqualTo(connectorFilter.getConnectorNames());
    assertThat(connectorFilterProperties.getCategories()).isEqualTo(connectorFilter.getCategories());
    assertThat(connectorFilterProperties.getConnectorIdentifiers())
        .isEqualTo(connectorFilter.getConnectorIdentifiers());
    assertThat(connectorFilterProperties.getDescription()).isEqualTo(connectorFilter.getDescription());
    assertThat(connectorFilterProperties.getTags().size()).isEqualTo(2);
    assertThat(connectorFilterProperties.getConnectorConnectivityModes())
        .isEqualTo(connectorFilter.getConnectorConnectivityModes());
  }
}
