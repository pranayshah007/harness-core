/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class PipelineYamlDtoMapper {
  public PipelineConfig toDto(PipelineEntity entity) {
    try {
      return YamlUtils.read(entity.getYaml(), PipelineConfig.class);
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot create pipeline yaml: " + ex.getMessage(), ex);
    }
  }

  public PipelineEntity toEntity(PipelineConfig dto, String accountIdentifier) {
    // todo(naman): look how to not use getYamlstring
    PipelineInfoConfig infoDto = dto.getPipelineInfoConfig();
    return PMSPipelineDtoMapper.toPipelineEntity(
        accountIdentifier, infoDto.getOrgIdentifier(), infoDto.getProjectIdentifier(), NGYamlUtils.getYamlString(dto));
  }

  public PipelineConfig toDto(String yaml) {
    try {
      return YamlUtils.read(yaml, PipelineConfig.class);
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot create pipeline yaml: " + ex.getMessage(), ex);
    }
  }
}
