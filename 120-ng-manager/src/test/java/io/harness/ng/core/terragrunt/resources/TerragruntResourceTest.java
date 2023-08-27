/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.terragrunt.resources;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.TerragruntCommandFlagType;
import io.harness.cdng.manifest.yaml.TerragruntStepsForCliOptions;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerragruntResourceTest extends CategoryTest {
  private final TerragruntResource terragruntResource = new TerragruntResource();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetTerragruntCommandFlagsForTerragruntSteps() {
    assertThat(terragruntResource.getTerragruntCommandFlags(TerragruntStepsForCliOptions.PLAN).getData())
        .containsExactlyInAnyOrder(
            TerragruntCommandFlagType.INIT, TerragruntCommandFlagType.WORKSPACE, TerragruntCommandFlagType.PLAN);
    assertThat(terragruntResource.getTerragruntCommandFlags(TerragruntStepsForCliOptions.APPLY).getData())
        .containsExactlyInAnyOrder(TerragruntCommandFlagType.INIT, TerragruntCommandFlagType.WORKSPACE,
            TerragruntCommandFlagType.PLAN, TerragruntCommandFlagType.APPLY);
    assertThat(terragruntResource.getTerragruntCommandFlags(TerragruntStepsForCliOptions.DESTROY).getData())
        .containsExactlyInAnyOrder(TerragruntCommandFlagType.INIT, TerragruntCommandFlagType.WORKSPACE,
            TerragruntCommandFlagType.APPLY, TerragruntCommandFlagType.DESTROY);
    assertThat(terragruntResource.getTerragruntCommandFlags(TerragruntStepsForCliOptions.ROLLBACK).getData())
        .containsExactlyInAnyOrder(TerragruntCommandFlagType.INIT, TerragruntCommandFlagType.WORKSPACE,
            TerragruntCommandFlagType.APPLY, TerragruntCommandFlagType.DESTROY);
  }
}
