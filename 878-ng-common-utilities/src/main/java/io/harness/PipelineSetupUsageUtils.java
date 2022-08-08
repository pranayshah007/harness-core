/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.beans.IdentifierRef;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PipelineSetupUsageUtils {
  public List<EntityDetail> extractInputReferredEntityFromYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineYaml, List<EntitySetupUsageDTO> allReferredUsages) {
    Map<String, Object> fqnToObjectMapMergedYaml = new HashMap<>();
    try {
      Map<FQN, Object> fqnObjectMap =
          FQNMapGenerator.generateFQNMap(YamlUtils.readTree(pipelineYaml).getNode().getCurrJsonNode());
      fqnObjectMap.keySet().forEach(fqn -> fqnToObjectMapMergedYaml.put(fqn.getExpressionFqn(), fqnObjectMap.get(fqn)));
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid merged pipeline yaml");
    }

    List<EntityDetail> entityDetails = new ArrayList<>();
    for (EntitySetupUsageDTO referredUsage : allReferredUsages) {
      IdentifierRef ref = (IdentifierRef) referredUsage.getReferredEntity().getEntityRef();
      Map<String, String> metadata = ref.getMetadata();
      if (metadata == null) {
        continue;
      }
      String fqn = metadata.get(PreFlightCheckMetadata.FQN);

      if (!metadata.containsKey(PreFlightCheckMetadata.EXPRESSION)) {
        entityDetails.add(referredUsage.getReferredEntity());
      } else if (fqnToObjectMapMergedYaml.containsKey(fqn)) {
        Object finalNode = fqnToObjectMapMergedYaml.get(fqn);
        if (finalNode instanceof ArrayNode) {
          ((ArrayNode) finalNode)
              .forEach(node
                  -> entityDetails.add(getEntityDetailFromTextNode(
                      accountIdentifier, orgIdentifier, projectIdentifier, (TextNode) node, referredUsage, metadata)));
        } else {
          entityDetails.add(getEntityDetailFromTextNode(
              accountIdentifier, orgIdentifier, projectIdentifier, (TextNode) finalNode, referredUsage, metadata));
        }
      }
    }
    return entityDetails.stream().filter(Objects::nonNull).collect(Collectors.toList());
  }

  private EntityDetail getEntityDetailFromTextNode(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, TextNode node, EntitySetupUsageDTO referredUsage, Map<String, String> metadata) {
    String finalValue = node.asText();
    if (NGExpressionUtils.isRuntimeOrExpressionField(finalValue)) {
      return null;
    }
    if (ParameterField.containsInputSetValidator(finalValue)) {
      finalValue = ParameterField.getValueFromParameterFieldWithInputSetValidator(finalValue);
    }
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(finalValue, accountIdentifier, orgIdentifier, projectIdentifier, metadata);
    return EntityDetail.builder()
        .name(referredUsage.getReferredEntity().getName())
        .type(referredUsage.getReferredEntity().getType())
        .entityRef(identifierRef)
        .build();
  }
}
