/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.interrupt;

import static io.harness.rule.OwnerRule.SAHIL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.service.InterruptProtoServiceGrpc.InterruptProtoServiceBlockingStub;
import io.harness.pms.contracts.service.InterruptRequest;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
@PrepareForTest(InterruptProtoServiceBlockingStub.class)
public class PMSInterruptServiceGrpcImplTest extends PmsSdkCoreTestBase {
  private static final String NOTIFY_ID = "notifyId";
  @Mock InterruptProtoServiceBlockingStub interruptProtoServiceBlockingStub;
  @InjectMocks PMSInterruptServiceGrpcImpl pmsInterruptServiceGrpc;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleAbort() {
    pmsInterruptServiceGrpc.handleAbort(NOTIFY_ID);
    Mockito.verify(interruptProtoServiceBlockingStub)
        .handleAbort(InterruptRequest.newBuilder().setNotifyId(NOTIFY_ID).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleFailure() {
    pmsInterruptServiceGrpc.handleFailure(NOTIFY_ID);
    Mockito.verify(interruptProtoServiceBlockingStub)
        .handleFailure(InterruptRequest.newBuilder().setNotifyId(NOTIFY_ID).build());
  }
}
