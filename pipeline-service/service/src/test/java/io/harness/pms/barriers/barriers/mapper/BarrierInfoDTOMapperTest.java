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
import io.harness.pms.barriers.mapper.BarrierInfoDTOMapper;
import io.harness.pms.barriers.response.BarrierInfoDTO;
import io.harness.rule.Owner;

import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierInfoDTOMapperTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToBarrierInfoDTO() {
    BarrierExecutionInfo barrierSetupInfo =
        BarrierExecutionInfo.builder().name("name").timeoutIn(1L).stages(new HashSet<>()).build();
    BarrierInfoDTO barrierSetupInfoDTO = BarrierInfoDTOMapper.toBarrierInfoDTO.apply(barrierSetupInfo);
    assertEquals(barrierSetupInfoDTO.getTimeoutIn(), barrierSetupInfo.getTimeoutIn());
    assertEquals(barrierSetupInfoDTO.getName(), barrierSetupInfo.getName());
    assertEquals(barrierSetupInfoDTO.getStages().size(), 0);
  }
}
