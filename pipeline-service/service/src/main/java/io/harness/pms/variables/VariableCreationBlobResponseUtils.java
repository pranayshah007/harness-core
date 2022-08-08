/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.variables;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.VariablesCreationBlobResponse;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.plan.creation.PlanCreationBlobResponseUtils;
import io.harness.pms.variables.VariableMergeServiceResponse.ServiceExpressionProperties;
import io.harness.pms.variables.VariableMergeServiceResponse.VariableResponseMapValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class VariableCreationBlobResponseUtils {
  public VariableMergeServiceResponse getMergeServiceResponse(String yaml, VariablesCreationBlobResponse response,
      Map<String, List<String>> serviceExpressionMap, boolean newVersion) {
    Map<String, VariableResponseMapValue> metadataMap = new LinkedHashMap<>();
    // Add Yaml Properties
    response.getYamlPropertiesMap().forEach(
        (k, v) -> metadataMap.put(k, VariableResponseMapValue.builder().yamlProperties(v).build()));

    // Add Yaml Output Properties
    if (!newVersion) {
      response.getYamlOutputPropertiesMap().keySet().forEach(uuid -> {
        YamlOutputProperties yamlOutputProperties = response.getYamlOutputPropertiesMap().get(uuid);
        if (metadataMap.containsKey(uuid)) {
          metadataMap.get(uuid).setYamlOutputProperties(yamlOutputProperties);
        } else {
          metadataMap.put(uuid, VariableResponseMapValue.builder().yamlOutputProperties(yamlOutputProperties).build());
        }
      });
    } else {
      response.getYamlExtraPropertiesMap().keySet().forEach(uuid -> {
        YamlExtraProperties yamlExtraProperties = response.getYamlExtraPropertiesMap().get(uuid);
        if (metadataMap.containsKey(uuid)) {
          metadataMap.get(uuid).setYamlExtraProperties(yamlExtraProperties);
        } else {
          metadataMap.put(uuid, VariableResponseMapValue.builder().yamlExtraProperties(yamlExtraProperties).build());
        }
      });
    }
    List<String> errorMessages = new ArrayList<>();
    response.getErrorResponseList().forEach(error -> {
      int messagesCount = error.getMessagesCount();
      for (int i = 0; i < messagesCount; i++) {
        errorMessages.add(error.getMessages(i));
      }
    });
    return VariableMergeServiceResponse.builder()
        .yaml(yaml)
        .metadataMap(metadataMap)
        .errorResponses(isNotEmpty(errorMessages) ? errorMessages : null)
        .serviceExpressionPropertiesList(getExpressionsFromMap(serviceExpressionMap))
        .build();
  }

  public void mergeResponses(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse, boolean newVersion) {
    if (otherResponse == null) {
      return;
    }

    mergeYamlProperties(builder, otherResponse);
    if (newVersion) {
      mergeYamlExtraProperties(builder, otherResponse);
    } else {
      mergeYamlOutputProperties(builder, otherResponse);
    }
    mergeResolvedDependencies(builder, otherResponse);
    mergeDependencies(builder, otherResponse);
    mergeErrorResponses(builder, otherResponse);
    addYamlUpdates(builder, otherResponse);
  }

  public static void mergeErrorResponses(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse) {
    if (isNotEmpty(otherResponse.getErrorResponseList())) {
      otherResponse.getErrorResponseList().forEach(builder::addErrorResponse);
    }
  }

  public void mergeProperties(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse, boolean newVersion) {
    mergeYamlProperties(builder, otherResponse);
    if (!newVersion) {
      mergeYamlOutputProperties(builder, otherResponse);
    } else {
      mergeYamlExtraProperties(builder, otherResponse);
    }
    String updatedPipelineJson = PlanCreationBlobResponseUtils.mergeYamlUpdates(
        builder.getDeps().getYaml(), otherResponse.getYamlUpdates().getFqnToYamlMap());
    builder.setDeps(builder.getDeps().toBuilder().setYaml(updatedPipelineJson).build());
  }

  private void mergeYamlProperties(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse) {
    if (isNotEmpty(otherResponse.getYamlPropertiesMap())) {
      otherResponse.getYamlPropertiesMap().forEach(builder::putYamlProperties);
    }
  }

  private void mergeYamlOutputProperties(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse) {
    if (isNotEmpty(otherResponse.getYamlOutputPropertiesMap())) {
      otherResponse.getYamlOutputPropertiesMap().forEach(builder::putYamlOutputProperties);
    }
  }

  private void mergeYamlExtraProperties(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse) {
    if (isNotEmpty(otherResponse.getYamlExtraPropertiesMap())) {
      otherResponse.getYamlExtraPropertiesMap().forEach(builder::putYamlExtraProperties);
    }
  }

  public List<ServiceExpressionProperties> getExpressionsFromMap(Map<String, List<String>> serviceExpressionMap) {
    List<ServiceExpressionProperties> serviceExpressionProperties = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : serviceExpressionMap.entrySet()) {
      entry.getValue()
          .stream()
          .map(e -> ServiceExpressionProperties.builder().serviceName(entry.getKey()).expression(e).build())
          .forEachOrdered(serviceExpressionProperties::add);
    }
    return serviceExpressionProperties;
  }

  public void mergeResolvedDependencies(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse) {
    if (isNotEmpty(otherResponse.getResolvedDeps().getDependenciesMap())) {
      otherResponse.getResolvedDeps().getDependenciesMap().forEach((key, value) -> {
        builder.setResolvedDeps(builder.getResolvedDeps().toBuilder().putDependencies(key, value));
        builder.setDeps(builder.getDeps().toBuilder().removeDependencies(key));
      });
    }
  }

  public void mergeDependencies(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse otherResponse) {
    if (isNotEmpty(otherResponse.getDeps().getDependenciesMap())) {
      otherResponse.getDeps().getDependenciesMap().forEach((key, value) -> {
        if (!builder.getResolvedDeps().containsDependencies(key)) {
          builder.setDeps(builder.getDeps().toBuilder().putDependencies(key, value));
        }
      });
    }
  }

  public VariablesCreationBlobResponse addYamlUpdates(
      VariablesCreationBlobResponse.Builder builder, VariablesCreationBlobResponse currResponse) {
    if (EmptyPredicate.isEmpty(currResponse.getYamlUpdates().getFqnToYamlMap())) {
      return builder.build();
    }
    Map<String, String> yamlUpdateFqnMap = new LinkedHashMap<>(builder.getYamlUpdates().getFqnToYamlMap());
    yamlUpdateFqnMap.putAll(currResponse.getYamlUpdates().getFqnToYamlMap());
    builder.setYamlUpdates(YamlUpdates.newBuilder().putAllFqnToYaml(yamlUpdateFqnMap).build());
    return builder.build();
  }
}
