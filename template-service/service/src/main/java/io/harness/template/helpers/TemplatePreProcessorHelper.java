/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessor;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.pms.yaml.preprocess.YamlV1PreProcessor;
import io.harness.template.entity.TemplateEntity;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class TemplatePreProcessorHelper {
  @Inject private YamlPreProcessorFactory yamlPreProcessorFactory;

  public Map<String, Object> preProcessResMap(TemplateEntity templateEntity, Map<String, Object> resMap,
      Set<String> idsValuesSet, Map<String, Integer> idsSuffixMap) {
    YamlPreProcessor yamlPreProcessor = yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1);
    if (templateEntity.getTemplateEntityType() == TemplateEntityType.PIPELINE_TEMPLATE) {
      if (HarnessYamlVersion.isV1(templateEntity.getHarnessVersion())) {
        resMap = JsonPipelineUtils.jsonNodeToMap(
            yamlPreProcessor.preProcess(JsonPipelineUtils.asTree(resMap)).getPreprocessedJsonNode());
        return resMap;
      } else {
        return resMap;
      }
    } else {
      // Special handling is required here because we need to set identifier for v0 templates and id for v1 templates
      if (HarnessYamlVersion.isV1(templateEntity.getHarnessVersion())) {
        resMap = preProcessResMapInternal(resMap, HarnessYamlVersion.V1, idsValuesSet, idsSuffixMap, yamlPreProcessor);
        return resMap;
      } else {
        resMap = preProcessResMapInternal(resMap, HarnessYamlVersion.V0, idsValuesSet, idsSuffixMap, yamlPreProcessor);
        return resMap;
      }
    }
  }

  private Map<String, Object> preProcessResMapInternal(Map<String, Object> resMap, String version,
      Set<String> idsValuesSet, Map<String, Integer> idsSuffixMap, YamlPreProcessor yamlPreprocessor) {
    injectIdInYaml(version, resMap, idsValuesSet, idsSuffixMap, yamlPreprocessor);
    switch (version) {
      case HarnessYamlVersion.V0:
        break;
      case HarnessYamlVersion.V1:
        resMap = JsonPipelineUtils.jsonNodeToMap(
            yamlPreprocessor.preProcess(JsonPipelineUtils.asTree(resMap)).getPreprocessedJsonNode());
        break;
      default:
        log.warn("Version {} not supported", version);
    }
    return resMap;
  }

  // Based on version, we are putting id or identifier in the resMap
  private void injectIdInYaml(String version, Map<String, Object> resMap, Set<String> idsValuesSet,
      Map<String, Integer> idsSuffixMap, YamlPreProcessor yamlPreprocessor) {
    if (resMap.get(YAMLFieldNameConstants.ID) != null) {
      resMap.remove(YAMLFieldNameConstants.ID);
    }
    String idField;
    switch (version) {
      case HarnessYamlVersion.V0:
        idField = YAMLFieldNameConstants.IDENTIFIER;
        break;
      case HarnessYamlVersion.V1:
        idField = YAMLFieldNameConstants.ID;
        break;
      default:
        log.warn("Version {} not supported", version);
        return;
    }
    if (resMap.get(idField) == null) {
      String id = yamlPreprocessor.getIdFromNameOrType(resMap);
      String idWithSuffix = yamlPreprocessor.getMaxSuffixForAnId(idsValuesSet, idsSuffixMap, id);
      // Set the generated unique id in the map.
      resMap.put(idField, TextNode.valueOf(idWithSuffix));
    }
  }

  public void collectIdsFromTemplateYaml(TemplateEntity templateEntity, Set<String> idsValuesSet) {
    if (HarnessYamlVersion.isV1(templateEntity.getHarnessVersion())) {
      YamlV1PreProcessor yamlPreProcessor =
          (YamlV1PreProcessor) yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1);
      yamlPreProcessor.collectExistingIdsFromYamlRecursively(
          YamlUtils.readAsJsonNode(templateEntity.getYaml()), idsValuesSet);
    }
  }
}
