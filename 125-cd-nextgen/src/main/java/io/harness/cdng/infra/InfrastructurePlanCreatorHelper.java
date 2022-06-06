/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class InfrastructurePlanCreatorHelper {
  public List<InfrastructureConfig> getResolvedInfrastructureConfig(
      List<InfrastructureEntity> infrastructureEntityList, Map<String, Map<String, Object>> refToInputMap) {
    List<InfrastructureConfig> infrastructureConfigs = new ArrayList<>();
    for (InfrastructureEntity entity : infrastructureEntityList) {
      String mergedInfraYaml = entity.getYaml();

      if (refToInputMap.containsKey(entity.getIdentifier())) {
        Map<String, Object> infraInputYaml = new HashMap<>();
        infraInputYaml.put(YamlTypes.INFRASTRUCTURE_DEF, refToInputMap.get(entity.getIdentifier()));
        mergedInfraYaml = MergeHelper.mergeInputSetFormatYamlToOriginYaml(
            entity.getYaml(), YamlPipelineUtils.writeYamlString(infraInputYaml));
      }

      try {
        infrastructureConfigs.add(YamlUtils.read(mergedInfraYaml, InfrastructureConfig.class));
      } catch (IOException e) {
        throw new InvalidRequestException(
            format("Failed to resolve infrastructure inputs %s ", entity.getIdentifier()));
      }
    }
    return infrastructureConfigs;
  }
}