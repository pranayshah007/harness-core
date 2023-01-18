/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.GITOPS)
public class GitOpsStepHelperTest extends CategoryTest {
  @Mock K8sStepHelper k8sStepHelper;
  @InjectMocks GitOpsStepHelper gitOpsStepHelper;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetReleaseRepoOutcome() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    doReturn(manifestsOutcome).when(k8sStepHelper).resolveManifestsOutcome(ambiance);

    Assertions.assertThatThrownBy(() -> gitOpsStepHelper.getReleaseRepoOutcome(ambiance))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Release Repo Manifest is mandatory");
  }
}
