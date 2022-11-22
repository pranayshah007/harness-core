/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.helpers.YamlRefreshHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
@Slf4j
public class InputSetMergeUtility {
  public static final String DUMMY_NODE = "dummy";
  public String mergeInputs(String oldInputsYaml, String newInputsYaml) {
    if (isEmpty(newInputsYaml)) {
      return newInputsYaml;
    }
    if (isEmpty(oldInputsYaml)) {
      return newInputsYaml;
    }
    return YamlPipelineUtils.writeYamlString(YamlRefreshHelper.refreshYamlFromSourceYaml(oldInputsYaml, newInputsYaml));
  }

  public String mergeArrayNodeInputs(String oldInputsYaml, String newInputsYaml) throws IOException {
    if (isEmpty(newInputsYaml)) {
      return newInputsYaml;
    }
    if (isEmpty(oldInputsYaml)) {
      return newInputsYaml;
    }
    ObjectMapper mapper = new ObjectMapper();
    JsonNode oldInputJsonNode = readTree(oldInputsYaml);
    addDummyRootToJsonNode(readTree(oldInputsYaml), mapper);
    JsonNode newInputJsonNode = addDummyRootToJsonNode(readTree(newInputsYaml), mapper);

    JsonNode refreshedJsonNode = YamlRefreshHelper.refreshYamlFromSourceYaml(
        YamlPipelineUtils.writeYamlString(oldInputJsonNode), YamlPipelineUtils.writeYamlString(newInputJsonNode));

    return refreshedJsonNode == null ? null : YamlPipelineUtils.writeYamlString(refreshedJsonNode.get(DUMMY_NODE));
  }

  private JsonNode addDummyRootToJsonNode(JsonNode node, ObjectMapper mapper) {
    ObjectNode dummyObjectNode = mapper.createObjectNode();
    dummyObjectNode.set(DUMMY_NODE, node);
    return dummyObjectNode;
  }

  private JsonNode readTree(String yaml) {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getCurrJsonNode();
    } catch (IOException e) {
      log.error("Error while converting yaml to jsonNode");
      throw new InvalidRequestException("Exception occurred while converting yaml to jsonNode");
    }
  }
}
