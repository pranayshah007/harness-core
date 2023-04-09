/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.hooks.steps.ServiceHooksOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class ServiceOutcomeHelperTest extends CDNGTestBase {
  @Mock private OutcomeService outcomeService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  ServiceStepOutcome serviceStepOutcome = ServiceStepOutcome.builder().name("service").identifier("service").build();
  ArtifactsOutcome artifactOutcome = ArtifactsOutcome.builder().build();
  ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
  private final ConfigFilesOutcome configFilesOutCm = new ConfigFilesOutcome();
  private final ServiceHooksOutcome serviceHooksOutcome = new ServiceHooksOutcome();
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFinalVariablesMap() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    doReturn(OptionalOutcome.builder().found(true).outcome(serviceStepOutcome).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    doReturn(OptionalOutcome.builder().found(true).outcome(artifactOutcome).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    doReturn(OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    doReturn(OptionalOutcome.builder().found(true).outcome(configFilesOutCm).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.CONFIG_FILES));
    doReturn(OptionalOutcome.builder().found(true).outcome(serviceHooksOutcome).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE_HOOKS));
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    ServiceConfigStepOutcome outcome =
        ServiceOutcomeHelper.createOutcome(ambiance, outcomeService, executionSweepingOutputService);
    assertThat(outcome).isNotNull();
    assertThat(outcome.getServiceResult().getName()).isEqualTo("service");
    assertThat(outcome.getServiceResult().getIdentifier()).isEqualTo("service");
    assertThat(outcome.getArtifactResults()).isEqualTo(artifactOutcome);
  }
}
