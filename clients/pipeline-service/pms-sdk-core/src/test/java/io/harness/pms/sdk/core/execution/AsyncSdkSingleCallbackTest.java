/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.mockito.ArgumentMatchers.any;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.invokers.AsyncStrategy;
import io.harness.rule.Owner;
import io.harness.waiter.StringNotifyResponseData;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AsyncSdkSingleCallbackTest extends PmsSdkCoreTestBase {
  @Mock AsyncStrategy strategy;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNotify() {
    String callbackId = generateUuid();
    List<String> allCallbackIds = ImmutableList.of(generateUuid(), callbackId);
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    StringNotifyResponseData responseData = StringNotifyResponseData.builder().data("RESPONSE_DATA").build();
    byte[] stepParameters = {};
    AsyncSdkSingleCallback callback = AsyncSdkSingleCallback.builder()
                                          .ambianceBytes(ambiance.toByteArray())
                                          .stepParameters(stepParameters)
                                          .allCallbackIds(allCallbackIds)
                                          .strategy(strategy)
                                          .build();

    callback.notify(ImmutableMap.of(callbackId, responseData));
    Mockito.verify(strategy).resumeSingle(
        Mockito.eq(ambiance), any(), Mockito.eq(allCallbackIds), Mockito.eq(callbackId), Mockito.eq(responseData));
  }
}
