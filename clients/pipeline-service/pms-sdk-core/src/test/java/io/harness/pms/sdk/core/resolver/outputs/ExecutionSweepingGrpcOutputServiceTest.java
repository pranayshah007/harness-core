/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.outputs;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.service.OptionalSweepingOutputResolveBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputConsumeBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputResolveBlobRequest;
import io.harness.pms.contracts.service.SweepingOutputResolveBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputServiceGrpc;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest({SweepingOutputServiceGrpc.SweepingOutputServiceBlockingStub.class})
public class ExecutionSweepingGrpcOutputServiceTest extends PmsSdkCoreTestBase {
  @Mock SweepingOutputServiceGrpc.SweepingOutputServiceBlockingStub sweepingOutputServiceBlockingStub;
  @InjectMocks ExecutionSweepingGrpcOutputService executionSweepingGrpcOutputService;

  public void initialize() {
    Mockito.mock(sweepingOutputServiceBlockingStub.getClass());
    executionSweepingGrpcOutputService = new ExecutionSweepingGrpcOutputService(sweepingOutputServiceBlockingStub);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testResolve() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject("test");
    SweepingOutputResolveBlobRequest sweepingOutputResolveBlobRequest =
        SweepingOutputResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build();
    when(sweepingOutputServiceBlockingStub.resolve(sweepingOutputResolveBlobRequest))
        .thenReturn(SweepingOutputResolveBlobResponse.newBuilder().build());

    executionSweepingGrpcOutputService.resolve(ambiance, refObject);
    verify(sweepingOutputServiceBlockingStub).resolve(sweepingOutputResolveBlobRequest);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testResolveOptional() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject("test");
    SweepingOutputResolveBlobRequest sweepingOutputResolveBlobRequest =
        SweepingOutputResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build();
    when(sweepingOutputServiceBlockingStub.resolveOptional(sweepingOutputResolveBlobRequest))
        .thenReturn(OptionalSweepingOutputResolveBlobResponse.newBuilder().build());

    executionSweepingGrpcOutputService.resolveOptional(ambiance, refObject);
    verify(sweepingOutputServiceBlockingStub).resolveOptional(sweepingOutputResolveBlobRequest);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testConsume() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    TestExecutionSweepingOutput test = TestExecutionSweepingOutput.builder().build();
    when(sweepingOutputServiceBlockingStub.consume(any()))
        .thenReturn(SweepingOutputConsumeBlobResponse.newBuilder().setResponse("test").build());

    executionSweepingGrpcOutputService.consume(ambiance, "test", test, "test");

    verify(sweepingOutputServiceBlockingStub).consume(any());
  }
}
