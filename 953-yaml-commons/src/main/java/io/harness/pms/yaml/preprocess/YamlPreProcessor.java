/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.preprocess;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
public interface YamlPreProcessor {
  // Adds ids in all the stages and steps where it doesn't already exists
  YamlPreprocessorResponseDTO preProcess(String yaml);
  YamlPreprocessorResponseDTO preProcess(JsonNode jsonNode);

  // Adding both implementations (with map and with jsonNode) to avoid deserialization
  default String getIdFromNameOrType(Map<String, Object> jsonNode) {
    if (jsonNode == null) {
      return null;
    }
    String id = null;
    if (jsonNode.get(YAMLFieldNameConstants.NAME) != null
        && jsonNode.get(YAMLFieldNameConstants.NAME) instanceof TextNode) {
      id = ((TextNode) jsonNode.get(YAMLFieldNameConstants.NAME)).asText();
    } else if (jsonNode.get(YAMLFieldNameConstants.TYPE) != null
        && jsonNode.get(YAMLFieldNameConstants.TYPE) instanceof TextNode) {
      id = ((TextNode) jsonNode.get(YAMLFieldNameConstants.TYPE)).asText();
    }
    if (id == null) {
      return null;
    }
    return IdentifierGeneratorUtils.getId(id);
  }

  default String getIdFromNameOrType(JsonNode jsonNode) {
    if (jsonNode == null) {
      return null;
    }
    String id = null;
    if (jsonNode.get(YAMLFieldNameConstants.NAME) != null && jsonNode.get(YAMLFieldNameConstants.NAME).isTextual()) {
      id = jsonNode.get(YAMLFieldNameConstants.NAME).asText();
    } else if (jsonNode.get(YAMLFieldNameConstants.TYPE) != null
        && jsonNode.get(YAMLFieldNameConstants.TYPE).isTextual()) {
      id = jsonNode.get(YAMLFieldNameConstants.TYPE).asText();
    }
    if (id == null) {
      return null;
    }
    return IdentifierGeneratorUtils.getId(id);
  }

  default String getIdWithSuffix(Set<String> idsValuesSet, String id) {
    // get the last index that was used as suffix with this id previously.
    String idSuffix = RandomStringUtils.randomAlphanumeric(4);
    String idWithSuffix = id + "_" + idSuffix;
    while (idsValuesSet.contains(idWithSuffix)) {
      idSuffix = RandomStringUtils.randomAlphanumeric(4);
      idWithSuffix = id + "_" + idSuffix;
    }
    return idWithSuffix;
  }
}
