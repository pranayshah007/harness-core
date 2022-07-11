/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto.converter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.LevelDTO;
import io.harness.pms.contracts.ambiance.Level;

import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class LevelDTOConverter {
  public Function<Level, LevelDTO> toLevelDTO = level
      -> LevelDTO.builder()
             .identifier(level.getIdentifier())
             .setupId(level.getSetupId())
             .runtimeId(level.getRuntimeId())
             .group(level.getGroup())
             .stepType(level.getStepType().getType())
             .skipExpressionChain(level.getSkipExpressionChain())
             .build();
}
