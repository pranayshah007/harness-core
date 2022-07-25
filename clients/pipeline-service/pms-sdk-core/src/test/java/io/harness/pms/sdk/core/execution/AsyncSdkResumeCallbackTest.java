/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.rule.Owner;

import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class AsyncSdkResumeCallbackTest extends PmsSdkCoreTestBase {
  private static String NODE_EXECUTION_ID = "nodeId";
  private static String PLAN_EXECUTION_ID = "planExecutionId";

  @Mock SdkNodeExecutionService sdkNodeExecutionService;
  AsyncSdkResumeCallback asyncSdkResumeCallback;
  Ambiance ambiance;

  @Before
  public void setup() {
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .addLevels(Level.newBuilder().setRuntimeId(NODE_EXECUTION_ID).build())
                   .build();
    asyncSdkResumeCallback = AsyncSdkResumeCallback.builder()
                                 .sdkNodeExecutionService(sdkNodeExecutionService)
                                 .ambianceBytes(ambiance.toByteArray())
                                 .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testNotify() {
    asyncSdkResumeCallback.notify(new HashMap<>());
    verify(sdkNodeExecutionService).resumeNodeExecution(ambiance, new HashMap<>(), false);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testNotifyError() {
    asyncSdkResumeCallback.notifyError(new HashMap<>());
    verify(sdkNodeExecutionService).resumeNodeExecution(ambiance, new HashMap<>(), true);
  }
}
