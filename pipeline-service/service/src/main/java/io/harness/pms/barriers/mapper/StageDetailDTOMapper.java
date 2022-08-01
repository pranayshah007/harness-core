/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.mapper;

import io.harness.pms.barriers.response.StageDetailDTO;
import io.harness.steps.barriers.beans.StageDetail;

import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StageDetailDTOMapper {
  public final Function<StageDetail, StageDetailDTO> toStageDetailDTO =
      stageDetail -> StageDetailDTO.builder().name(stageDetail.getName()).build();
}
