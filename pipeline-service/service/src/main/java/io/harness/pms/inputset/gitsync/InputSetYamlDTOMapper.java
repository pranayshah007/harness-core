/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.gitsync;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InputSetYamlDTOMapper {
  public InputSetYamlDTO toDTO(InputSetEntity entity) {
    try {
      return YamlUtils.read(entity.getYaml(), InputSetYamlDTO.class);
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot create input set yaml: " + ex.getMessage(), ex);
    }
  }

  public InputSetEntity toEntity(InputSetYamlDTO dto, String accountIdentifier) {
    // todo(naman): look how to not use getYamlstring
    InputSetYamlInfoDTO inputSetInfo = dto.getInputSetInfo();
    return PMSInputSetElementMapper.toInputSetEntity(accountIdentifier, inputSetInfo.getOrgIdentifier(),
        inputSetInfo.getProjectIdentifier(), inputSetInfo.getPipelineInfoConfig().getIdentifier(),
        NGYamlUtils.getYamlString(dto));
  }

  public InputSetYamlDTO toDTO(String yaml) {
    try {
      return YamlUtils.read(yaml, InputSetYamlDTO.class);
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot create input set yaml: " + ex.getMessage(), ex);
    }
  }
}
