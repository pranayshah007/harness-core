/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.common.NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN;
import static io.harness.pms.yaml.YamlNode.PATH_SEP;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
@Singleton
public class ExpansionRequestsExtractor {
  @Inject ExpansionRequestsHelper expansionRequestsHelper;
  @Inject PmsSdkInstanceService pmsSdkInstanceService;

  public Set<ExpansionRequest> fetchExpansionRequests(String pipelineYaml) {
    YamlNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(pipelineYaml).getNode();
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read pipeline yaml", e);
    }
    Stack<ModuleType> namespace = new Stack<>();
    namespace.push(ModuleType.PMS);
    List<PmsSdkInstance> activeInstances = pmsSdkInstanceService.getActiveInstances();
    Map<ModuleType, Set<String>> expandableFieldsPerService =
        expansionRequestsHelper.getExpandableFieldsPerService(activeInstances);
    Map<String, ModuleType> typeToService = expansionRequestsHelper.getTypeToService(activeInstances);

    Set<ExpansionRequest> serviceCalls = new HashSet<>();
    getServiceCalls(pipelineNode, expandableFieldsPerService, typeToService, namespace, serviceCalls);
    List<LocalFQNExpansionInfo> localFQNRequestMetadata =
        expansionRequestsHelper.getLocalFQNRequestMetadata(activeInstances);
    if (EmptyPredicate.isNotEmpty(localFQNRequestMetadata)) {
      getFQNBasedServiceCalls(pipelineNode, localFQNRequestMetadata, serviceCalls);
    }
    return serviceCalls;
  }

  void getServiceCalls(YamlNode node, Map<ModuleType, Set<String>> expandableFieldsPerService,
      Map<String, ModuleType> typeToService, Stack<ModuleType> namespace, Set<ExpansionRequest> serviceCalls) {
    if (node.isObject()) {
      getServiceCallsForObject(node, expandableFieldsPerService, typeToService, namespace, serviceCalls);
    } else if (node.isArray()) {
      getServiceCallsForArray(node, expandableFieldsPerService, typeToService, namespace, serviceCalls);
    }
  }

  void getServiceCallsForObject(YamlNode node, Map<ModuleType, Set<String>> expandableFieldsPerService,
      Map<String, ModuleType> typeToService, Stack<ModuleType> namespace, Set<ExpansionRequest> serviceCalls) {
    List<String> keys = node.fetchKeys();
    boolean popNamespace = false;
    if (keys.contains(YAMLFieldNameConstants.TYPE) && typeToService.containsKey(node.getType())) {
      namespace.push(typeToService.get(node.getType()));
      popNamespace = true;
    }
    Set<String> expandableKeys = expandableFieldsPerService.get(namespace.peek()) != null
        ? expandableFieldsPerService.get(namespace.peek())
        : Collections.emptySet();
    for (String key : keys) {
      if (expandableKeys.contains(key)) {
        JsonNode value = node.getFieldOrThrow(key).getNode().getCurrJsonNode();
        if (value.isTextual() && NGExpressionUtils.containsPattern(GENERIC_EXPRESSIONS_PATTERN, value.textValue())) {
          continue;
        }
        ExpansionRequest request = ExpansionRequest.builder()
                                       .module(namespace.peek())
                                       .fqn(node.getYamlPath() + PATH_SEP + key)
                                       .key(key)
                                       .fieldValue(value)
                                       .build();
        serviceCalls.add(request);
        continue;
      }
      getServiceCalls(
          node.getFieldOrThrow(key).getNode(), expandableFieldsPerService, typeToService, namespace, serviceCalls);
    }
    if (popNamespace) {
      namespace.pop();
    }
  }

  void getServiceCallsForArray(YamlNode node, Map<ModuleType, Set<String>> expandableFieldsPerService,
      Map<String, ModuleType> typeToService, Stack<ModuleType> namespace, Set<ExpansionRequest> serviceCalls) {
    List<YamlNode> nodes = node.asArray();
    for (YamlNode internalNode : nodes) {
      getServiceCalls(internalNode, expandableFieldsPerService, typeToService, namespace, serviceCalls);
    }
  }

  void getFQNBasedServiceCalls(
      YamlNode pipelineNode, List<LocalFQNExpansionInfo> localFQNRequestMetadata, Set<ExpansionRequest> serviceCalls) {
    YamlNode internalNode = pipelineNode.getFieldOrThrow(YAMLFieldNameConstants.PIPELINE).getNode();
    List<YamlNode> stagesList = internalNode.getFieldOrThrow(YAMLFieldNameConstants.STAGES).getNode().asArray();
    for (YamlNode stageNode : stagesList) {
      if (stageNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        YamlNode parallelNode = stageNode.getFieldOrThrow(YAMLFieldNameConstants.PARALLEL).getNode();
        List<YamlNode> parallelStages = parallelNode.asArray();
        for (YamlNode parallelStage : parallelStages) {
          getServiceCallsForStage(parallelStage, localFQNRequestMetadata, serviceCalls);
        }
      } else {
        getServiceCallsForStage(stageNode, localFQNRequestMetadata, serviceCalls);
      }
    }
  }

  void getServiceCallsForStage(
      YamlNode stageNode, List<LocalFQNExpansionInfo> localFQNRequestMetadata, Set<ExpansionRequest> serviceCalls) {
    YamlNode stageNodeInternal = stageNode.getFieldOrThrow(YAMLFieldNameConstants.STAGE).getNode();
    String stageType = stageNodeInternal.getType();
    List<LocalFQNExpansionInfo> currStageRequestsData = getRequestsDataForStageType(localFQNRequestMetadata, stageType);
    if (EmptyPredicate.isNotEmpty(currStageRequestsData)) {
      getServiceCalls(stageNodeInternal, currStageRequestsData, serviceCalls);
    }
  }

  List<LocalFQNExpansionInfo> getRequestsDataForStageType(
      List<LocalFQNExpansionInfo> localFQNRequestMetadata, String stageType) {
    return localFQNRequestMetadata.stream()
        .filter(e -> stageType.equals(e.getStageType()))
        .collect(Collectors.toList());
  }

  void getServiceCalls(
      YamlNode node, List<LocalFQNExpansionInfo> currStageRequestsData, Set<ExpansionRequest> serviceCalls) {
    if (node.isObject()) {
      getServiceCallsForObject(node, currStageRequestsData, serviceCalls);
    } else if (node.isArray()) {
      getServiceCallsForArray(node, currStageRequestsData, serviceCalls);
    }
  }

  void getServiceCallsForObject(
      YamlNode node, List<LocalFQNExpansionInfo> currStageRequestsData, Set<ExpansionRequest> serviceCalls) {
    List<YamlField> fields = node.fields();
    for (YamlField field : fields) {
      YamlNode currNode = field.getNode();
      String yamlPath = currNode.getYamlPath();
      String localPath = currNode.extractStageLocalYamlPath();
      List<LocalFQNExpansionInfo> selectedExpansionRequestData =
          currStageRequestsData.stream().filter(e -> e.getLocalFQN().equals(localPath)).collect(Collectors.toList());
      selectedExpansionRequestData.forEach(e -> {
        ExpansionRequest expansionRequest = ExpansionRequest.builder()
                                                .module(e.getModule())
                                                .fqn(yamlPath)
                                                .key(localPath)
                                                .fieldValue(currNode.getCurrJsonNode())
                                                .build();
        serviceCalls.add(expansionRequest);
      });
      getServiceCalls(field.getNode(), currStageRequestsData, serviceCalls);
    }
  }

  void getServiceCallsForArray(
      YamlNode node, List<LocalFQNExpansionInfo> currStageRequestsData, Set<ExpansionRequest> serviceCalls) {
    List<YamlNode> nodes = node.asArray();
    for (YamlNode internalNode : nodes) {
      getServiceCalls(internalNode, currStageRequestsData, serviceCalls);
    }
  }
}
