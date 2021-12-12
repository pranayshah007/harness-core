package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YamlNode.PATH_SEP;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

@OwnedBy(PIPELINE)
@Singleton
public class ExpansionRequestsExtractor {
  @Inject ExpansionRequestsHelper expansionRequestsHelper;

  public Set<ExpansionRequest> fetchExpansionRequests(String pipelineYaml) {
    YamlNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(pipelineYaml).getNode();
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read pipeline yaml", e);
    }
    Stack<ModuleType> namespace = new Stack<>();
    namespace.push(ModuleType.PMS);
    Map<ModuleType, Set<String>> expandableFieldsPerService = expansionRequestsHelper.getExpandableFieldsPerService();
    Map<String, ModuleType> typeToService = expansionRequestsHelper.getTypeToService();

    Set<ExpansionRequest> serviceCalls = new HashSet<>();
    getServiceCalls(pipelineNode, expandableFieldsPerService, typeToService, namespace, serviceCalls);
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
        ExpansionRequest request = ExpansionRequest.builder()
                                       .module(namespace.peek())
                                       .fqn(node.getYamlPath() + PATH_SEP + key)
                                       .fieldValue(node.getField(key).getNode().getCurrJsonNode())
                                       .build();
        serviceCalls.add(request);
        continue;
      }
      getServiceCalls(node.getField(key).getNode(), expandableFieldsPerService, typeToService, namespace, serviceCalls);
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
}
