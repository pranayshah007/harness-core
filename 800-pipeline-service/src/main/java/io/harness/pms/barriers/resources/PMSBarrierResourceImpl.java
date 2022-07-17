/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.resources;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.barriers.beans.BarrierExecutionInfo;
import io.harness.pms.barriers.mapper.BarrierExecutionInfoDTOMapper;
import io.harness.pms.barriers.mapper.BarrierInfoDTOMapper;
import io.harness.pms.barriers.mapper.BarrierSetupInfoDTOMapper;
import io.harness.pms.barriers.response.BarrierExecutionInfoDTO;
import io.harness.pms.barriers.response.BarrierInfoDTO;
import io.harness.pms.barriers.response.BarrierSetupInfoDTO;
import io.harness.pms.barriers.service.PMSBarrierService;
import io.harness.steps.barriers.beans.BarrierSetupInfo;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
public class PMSBarrierResourceImpl implements PMSBarrierResource {
  private final PMSBarrierService pmsBarrierService;

  public ResponseDTO<List<BarrierSetupInfoDTO>> getBarriersSetupInfoList(@NotNull String yaml) {
    List<BarrierSetupInfo> barrierSetupInfoList = pmsBarrierService.getBarrierSetupInfoList(yaml);
    List<BarrierSetupInfoDTO> barrierSetupInfoDTOList =
        barrierSetupInfoList.stream().map(BarrierSetupInfoDTOMapper.toBarrierSetupInfoDTO).collect(Collectors.toList());
    return ResponseDTO.newResponse(barrierSetupInfoDTOList);
  }

  public ResponseDTO<List<BarrierExecutionInfoDTO>> getBarriersExecutionInfo(
      @NotNull String stageSetupId, @NotNull String planExecutionId) {
    List<BarrierExecutionInfo> barrierExecutionInfoList =
        pmsBarrierService.getBarrierExecutionInfoList(stageSetupId, planExecutionId);
    List<BarrierExecutionInfoDTO> barrierExecutionInfoDTOList =
        barrierExecutionInfoList.stream()
            .map(BarrierExecutionInfoDTOMapper.toBarrierExecutionInfoDTO)
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(barrierExecutionInfoDTOList);
  }

  public ResponseDTO<BarrierInfoDTO> getBarriersInfo(@NotNull String barrierSetupId, @NotNull String planExecutionId) {
    BarrierExecutionInfo barrierExecutionInfo =
        pmsBarrierService.getBarrierExecutionInfo(barrierSetupId, planExecutionId);
    return ResponseDTO.newResponse(BarrierInfoDTOMapper.toBarrierInfoDTO.apply(barrierExecutionInfo));
  }
}
