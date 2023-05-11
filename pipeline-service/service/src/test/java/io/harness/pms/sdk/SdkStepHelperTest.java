/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.rule.OwnerRule.SAHIL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.datastructures.EphemeralCacheService;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class SdkStepHelperTest extends PipelineServiceTestBase {
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock CommonStepInfo commonStepInfo;
  @Mock EphemeralCacheService ephemeralCacheService;
  @InjectMocks SdkStepHelper sdkStepHelper;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetAllStepsVisibleInUI() {
    when(ephemeralCacheService.getDistributedSet(any())).thenReturn(new HashSet<>());
    when(pmsSdkInstanceService.getSdkSteps()).thenReturn(new HashMap<>());
    when(commonStepInfo.getCommonSteps("")).thenReturn(new ArrayList<>());
    assertEquals(sdkStepHelper.getAllStepVisibleInUI().size(), 0);
    verify(pmsSdkInstanceService).getSdkSteps();
    verify(commonStepInfo).getCommonSteps("");
  }
}
