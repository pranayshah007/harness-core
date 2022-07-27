/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.barriers.mapper;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.barriers.beans.BarrierExecutionInfo;
import io.harness.pms.barriers.mapper.BarrierExecutionInfoDTOMapper;
import io.harness.pms.barriers.response.BarrierExecutionInfoDTO;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.StageDetail;

import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierExecutionInfoDTOMapperTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToBarrierExecutionInfoDTO() {
    BarrierExecutionInfo barrierExecutionInfo = BarrierExecutionInfo.builder()
                                                    .identifier("id")
                                                    .name("name")
                                                    .startedAt(1L)
                                                    .started(true)
                                                    .timeoutIn(10L)
                                                    .stages(new HashSet<StageDetail>() {})
                                                    .build();

    BarrierExecutionInfoDTO barrierExecutionInfoDTO =
        BarrierExecutionInfoDTOMapper.toBarrierExecutionInfoDTO.apply(barrierExecutionInfo);
    assertEquals(barrierExecutionInfo.getName(), barrierExecutionInfoDTO.getName());
    assertEquals(barrierExecutionInfo.getIdentifier(), barrierExecutionInfoDTO.getIdentifier());
    assertEquals(barrierExecutionInfo.getStartedAt(), barrierExecutionInfoDTO.getStartedAt());
    assertEquals(barrierExecutionInfo.isStarted(), barrierExecutionInfoDTO.isStarted());
    assertEquals(barrierExecutionInfo.getTimeoutIn(), barrierExecutionInfoDTO.getTimeoutIn());
    assertEquals(barrierExecutionInfoDTO.getStages().size(), 0);
  }
}
