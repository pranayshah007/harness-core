/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.rule.OwnerRule.MEENA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.exception.WingsException;
import io.harness.gitops.models.Application;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(GITOPS)
public class SyncStepTest extends CategoryTest {
  @InjectMocks private SyncStep syncStep;

  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testAsyncResponseWithValidResponse() {
    Ambiance ambiance = mock(Ambiance.class);
    StepElementParameters stepParameters = StepElementParameters.builder()
                                               .uuid("UUID")
                                               .spec(SyncStepParameters.infoBuilder().build())
                                               .timeout(ParameterField.createValueField("15m"))
                                               .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("GitOpsSyncUUID",
        SyncResponse.builder()
            .applicationsSucceededOnArgoSync(new HashSet<Application>())
            .applicationsFailedToSync(new HashSet<Application>())
            .syncStillRunningForApplications(new HashSet<Application>())
            .build());

    StepResponse response = syncStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertNotNull(response);
    assertEquals(Status.SUCCEEDED, response.getStatus());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testAsyncResponseWithErrorResponse() {
    Ambiance ambiance = mock(Ambiance.class);
    StepElementParameters stepParameters = StepElementParameters.builder()
                                               .uuid("UUID")
                                               .spec(SyncStepParameters.infoBuilder().build())
                                               .timeout(ParameterField.createValueField("15m"))
                                               .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("GitOpsSyncUUID", ErrorNotifyResponseData.builder().errorMessage("error occurred").build());

    assertThatThrownBy(() -> syncStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap))
        .isInstanceOf(WingsException.class);
  }
}
