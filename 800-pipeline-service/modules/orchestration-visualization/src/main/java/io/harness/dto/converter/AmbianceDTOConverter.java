/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto.converter;

import io.harness.dto.AmbianceDTO;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AmbianceDTOConverter {
  public Function<Ambiance, AmbianceDTO> toAmbianceDTO = ambiance
      -> AmbianceDTO.builder()
             .planExecutionId(ambiance.getPlanExecutionId())
             .setupAbstractions(ambiance.getSetupAbstractionsMap())
             .levels(ambiance.getLevelsList().stream().map(LevelDTOConverter.toLevelDTO).collect(Collectors.toList()))
             .build();
}
