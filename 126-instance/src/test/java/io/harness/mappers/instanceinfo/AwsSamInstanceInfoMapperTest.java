/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import static io.harness.rule.OwnerRule.SAINATH;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.instanceinfo.AwsSamInstanceInfoDTO;
import io.harness.entities.instanceinfo.AwsSamInstanceInfo;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamInstanceInfoMapperTest extends InstancesTestBase {
  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testToDTO() {
    AwsSamInstanceInfo awsSamInstanceInfo = AwsSamInstanceInfo.builder()
                                                .functionName("functionName")
                                                .region("region")
                                                .handler("handler")
                                                .memorySize("memorySize")
                                                .runTime("runTime")
                                                .infraStructureKey("infraStructureKey")
                                                .build();
    AwsSamInstanceInfoDTO awsSamInstanceInfoDTO = AwsSamInstanceInfoMapper.toDTO(awsSamInstanceInfo);
    assert (awsSamInstanceInfoDTO.getFunctionName()).equals(awsSamInstanceInfo.getFunctionName());
    assert (awsSamInstanceInfoDTO.getRegion()).equals(awsSamInstanceInfo.getRegion());
    assert (awsSamInstanceInfoDTO.getRegion()).equals(awsSamInstanceInfo.getRegion());
    assert (awsSamInstanceInfoDTO.getHandler()).equals(awsSamInstanceInfo.getHandler());
    assert (awsSamInstanceInfoDTO.getMemorySize()).equals(awsSamInstanceInfo.getMemorySize());
    assert (awsSamInstanceInfoDTO.getInfraStructureKey()).equals(awsSamInstanceInfo.getInfraStructureKey());
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testToEntity() {
    AwsSamInstanceInfoDTO awsSamInstanceInfoDTO = AwsSamInstanceInfoDTO.builder()
                                                      .functionName("functionName")
                                                      .region("region")
                                                      .handler("handler")
                                                      .memorySize("memorySize")
                                                      .runTime("runTime")
                                                      .infraStructureKey("infraStructureKey")
                                                      .build();

    AwsSamInstanceInfo awsSamInstanceInfo = AwsSamInstanceInfoMapper.toEntity(awsSamInstanceInfoDTO);

    assert (awsSamInstanceInfo.getFunctionName()).equals(awsSamInstanceInfoDTO.getFunctionName());
    assert (awsSamInstanceInfo.getRegion()).equals(awsSamInstanceInfoDTO.getRegion());
    assert (awsSamInstanceInfo.getRegion()).equals(awsSamInstanceInfoDTO.getRegion());
    assert (awsSamInstanceInfo.getHandler()).equals(awsSamInstanceInfoDTO.getHandler());
    assert (awsSamInstanceInfo.getMemorySize()).equals(awsSamInstanceInfoDTO.getMemorySize());
    assert (awsSamInstanceInfo.getInfraStructureKey()).equals(awsSamInstanceInfoDTO.getInfraStructureKey());
  }
}
