/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.pms.yaml.YamlNode.PATH_SEP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.YamlException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class YamlNodeUtils {
  public void addToPath(YamlNode yamlNode, String path, JsonNode newNode) {
    if (EmptyPredicate.isEmpty(path)) {
      return;
    }

    List<String> pathList = Arrays.asList(path.split(PATH_SEP));
    if (EmptyPredicate.isEmpty(pathList)) {
      return;
    }

    JsonNode curr = yamlNode.getCurrJsonNode();
    for (String currName : pathList) {
      if (curr == null) {
        return;
      }

      if (currName.charAt(0) == '[') {
        if (!curr.isArray()) {
          throw new YamlException(String.format("Trying to use index path (%s) on non-array node", currName));
        }
        try {
          int idx = Integer.parseInt(currName.substring(1, currName.length() - 1));
          curr = curr.get(idx);
        } catch (Exception ex) {
          throw new YamlException(String.format("Incorrect index path (%s) on array node", currName));
        }
      } else {
        curr = curr.get(currName);
      }
    }
    if (curr.isArray()) {
      ArrayNode arrayNode = (ArrayNode) curr;
      arrayNode.add(newNode);
    } else {
      ObjectNode objectNode = (ObjectNode) curr;
      objectNode.setAll((ObjectNode) newNode);
    }
  }
}
