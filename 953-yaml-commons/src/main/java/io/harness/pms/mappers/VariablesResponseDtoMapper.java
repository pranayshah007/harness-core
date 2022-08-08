/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.mappers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.service.ServiceExpressionPropertiesProto;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariableResponseMapValueProto;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.pms.variables.VariableMergeServiceResponse.ServiceExpressionProperties;
import io.harness.pms.variables.VariableMergeServiceResponse.VariableResponseMapValue;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VariablesResponseDtoMapper {
  public VariableMergeResponseProto toProto(VariableMergeServiceResponse variableMergeServiceResponse) {
    Map<String, VariableResponseMapValueProto> metadataMap =
        EmptyPredicate.isEmpty(variableMergeServiceResponse.getMetadataMap())
        ? Collections.emptyMap()
        : variableMergeServiceResponse.getMetadataMap().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, e -> toVariablesResponseMapProto(e.getValue())));
    return VariableMergeResponseProto.newBuilder()
        .setYaml(EmptyPredicate.isEmpty(variableMergeServiceResponse.getYaml())
                ? ""
                : variableMergeServiceResponse.getYaml())
        .putAllMetadataMap(EmptyPredicate.isEmpty(metadataMap) ? Collections.emptyMap() : metadataMap)
        .addAllErrorResponses(EmptyPredicate.isEmpty(variableMergeServiceResponse.getErrorResponses())
                ? Collections.emptyList()
                : variableMergeServiceResponse.getErrorResponses())
        .addAllServiceExpressionPropertiesList(
            EmptyPredicate.isEmpty(variableMergeServiceResponse.getServiceExpressionPropertiesList())
                ? Collections.emptyList()
                : variableMergeServiceResponse.getServiceExpressionPropertiesList()
                      .stream()
                      .map(VariablesResponseDtoMapper::toServiceExpressionProto)
                      .collect(Collectors.toList()))
        .build();
  }

  public VariableMergeServiceResponse toDto(VariableMergeResponseProto variableMergeResponseProto) {
    Map<String, VariableResponseMapValue> metadataMap =
        variableMergeResponseProto.getMetadataMapMap().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, e -> toVariablesResponseMapValue(e.getValue())));
    return VariableMergeServiceResponse.builder()
        .yaml(variableMergeResponseProto.getYaml())
        .errorResponses(variableMergeResponseProto.getErrorResponsesList())
        .metadataMap(metadataMap)
        .serviceExpressionPropertiesList(variableMergeResponseProto.getServiceExpressionPropertiesListList()
                                             .stream()
                                             .map(VariablesResponseDtoMapper::toServiceExpressionProperties)
                                             .collect(Collectors.toList()))
        .build();
  }

  private VariableResponseMapValueProto toVariablesResponseMapProto(VariableResponseMapValue variableResponseMapValue) {
    return VariableResponseMapValueProto.newBuilder()
        .setYamlProperties(variableResponseMapValue.getYamlProperties() == null
                ? YamlProperties.newBuilder().build()
                : variableResponseMapValue.getYamlProperties())
        .setYamlOutputProperties(variableResponseMapValue.getYamlOutputProperties() == null
                ? YamlOutputProperties.newBuilder().build()
                : variableResponseMapValue.getYamlOutputProperties())
        .setYamlExtraProperties(variableResponseMapValue.getYamlExtraProperties() == null
                ? YamlExtraProperties.newBuilder().build()
                : variableResponseMapValue.getYamlExtraProperties())
        .build();
  }

  private ServiceExpressionPropertiesProto toServiceExpressionProto(
      ServiceExpressionProperties serviceExpressionProperties) {
    return ServiceExpressionPropertiesProto.newBuilder()
        .setServiceName(EmptyPredicate.isEmpty(serviceExpressionProperties.getServiceName())
                ? ""
                : serviceExpressionProperties.getServiceName())
        .setExpression(EmptyPredicate.isEmpty(serviceExpressionProperties.getExpression())
                ? ""
                : serviceExpressionProperties.getExpression())
        .build();
  }

  private VariableResponseMapValue toVariablesResponseMapValue(
      VariableResponseMapValueProto variableResponseMapValueProto) {
    return VariableResponseMapValue.builder()
        .yamlOutputProperties(variableResponseMapValueProto.getYamlOutputProperties())
        .yamlProperties(variableResponseMapValueProto.getYamlProperties())
        .yamlExtraProperties(variableResponseMapValueProto.getYamlExtraProperties())
        .build();
  }

  private ServiceExpressionProperties toServiceExpressionProperties(
      ServiceExpressionPropertiesProto serviceExpressionPropertiesProto) {
    return ServiceExpressionProperties.builder()
        .serviceName(serviceExpressionPropertiesProto.getServiceName())
        .expression(serviceExpressionPropertiesProto.getExpression())
        .build();
  }
}
