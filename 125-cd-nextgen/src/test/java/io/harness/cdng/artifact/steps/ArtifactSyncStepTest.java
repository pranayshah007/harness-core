/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.steps;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.cdng.service.steps.ServiceStepsHelper;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactSyncStepTest extends CategoryTest {
  @Mock private ArtifactStepHelper artifactStepHelper;
  @Mock private ServiceStepsHelper serviceStepsHelper;

  @InjectMocks private ArtifactSyncStep artifactSyncStep;

  @Mock private NGLogCallback logCallback;
  private Ambiance ambiance = Ambiance.newBuilder().build();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(logCallback).when(serviceStepsHelper).getServiceLogCallback(ambiance);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteSync() {
    CustomArtifactConfig artifactConfig =
        CustomArtifactConfig.builder().version(ParameterField.createValueField("test")).build();
    ArtifactStepParameters artifactStepParameters = ArtifactStepParameters.builder().spec(artifactConfig).build();

    doReturn(artifactConfig).when(artifactStepHelper).applyArtifactsOverlay(artifactStepParameters);

    StepResponse response =
        artifactSyncStep.executeSync(ambiance, artifactStepParameters, StepInputPackage.builder().build(), null);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);
  }
}