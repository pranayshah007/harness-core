/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.service.ServiceExpressionPropertiesProto;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariableResponseMapValueProto;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.pms.variables.VariableMergeServiceResponse.VariableResponseMapValue;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class VariablesResponseDtoMapperTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testWriteProto() {
    // test empty response
    VariableMergeServiceResponse variableMergeServiceResponse = VariableMergeServiceResponse.builder().build();
    VariableMergeResponseProto variableMergeResponseProto =
        VariablesResponseDtoMapper.toProto(variableMergeServiceResponse);
    assertThat(variableMergeResponseProto).isNotNull();
    assertThat(variableMergeResponseProto.getYaml()).isEmpty();
    assertThat(variableMergeResponseProto.getMetadataMapMap()).isEmpty();
    assertThat(variableMergeResponseProto.getErrorResponsesList()).isEmpty();
    assertThat(variableMergeResponseProto.getServiceExpressionPropertiesListList()).isEmpty();

    // Filling partial data
    Map<String, VariableResponseMapValue> metadataMap = new HashMap<>();
    metadataMap.put(
        "v1", VariableResponseMapValue.builder().yamlProperties(YamlProperties.newBuilder().build()).build());
    variableMergeServiceResponse = VariableMergeServiceResponse.builder()
                                       .errorResponses(Collections.singletonList("temp1"))
                                       .metadataMap(metadataMap)
                                       .build();
    variableMergeResponseProto = VariablesResponseDtoMapper.toProto(variableMergeServiceResponse);
    assertThat(variableMergeResponseProto).isNotNull();
    assertThat(variableMergeResponseProto.getYaml()).isEmpty();
    assertThat(variableMergeResponseProto.getMetadataMapMap()).isNotEmpty();
    assertThat(variableMergeResponseProto.getErrorResponsesList()).isNotEmpty();
    assertThat(variableMergeResponseProto.getServiceExpressionPropertiesListList()).isEmpty();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testWriteDto() {
    // test empty response
    VariableMergeResponseProto mergeResponseProto = VariableMergeResponseProto.newBuilder().build();
    VariableMergeServiceResponse variableMergeServiceResponse = VariablesResponseDtoMapper.toDto(mergeResponseProto);
    assertThat(variableMergeServiceResponse).isNotNull();
    assertThat(variableMergeServiceResponse.getYaml()).isEmpty();
    assertThat(variableMergeServiceResponse.getMetadataMap()).isEmpty();
    assertThat(variableMergeServiceResponse.getErrorResponses()).isEmpty();
    assertThat(variableMergeServiceResponse.getServiceExpressionPropertiesList()).isEmpty();

    // Filling partial data
    Map<String, VariableResponseMapValueProto> metadataMap = new HashMap<>();
    metadataMap.put("v1",
        VariableResponseMapValueProto.newBuilder()
            .setYamlOutputProperties(YamlOutputProperties.newBuilder().build())
            .build());
    mergeResponseProto = VariableMergeResponseProto.newBuilder()
                             .putAllMetadataMap(metadataMap)
                             .setYaml("temp2")
                             .addServiceExpressionPropertiesList(
                                 ServiceExpressionPropertiesProto.newBuilder().setExpression("temp3").build())
                             .build();
    variableMergeServiceResponse = VariablesResponseDtoMapper.toDto(mergeResponseProto);
    assertThat(variableMergeServiceResponse.getYaml()).isEqualTo("temp2");
    assertThat(variableMergeServiceResponse.getMetadataMap()).isNotEmpty();
    assertThat(variableMergeServiceResponse.getErrorResponses()).isEmpty();
    assertThat(variableMergeServiceResponse.getServiceExpressionPropertiesList()).isNotEmpty();
  }
}