/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.terraform.resources;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.TerraformCommandFlagType;
import io.harness.cdng.manifest.yaml.TerraformStepsForCliOptions;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerraformResourceTest extends CategoryTest {
  private final TerraformResource terraformResource = new TerraformResource();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetHelmCommandFlagsForHelmServiceSpec() {
    assertThat(terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.PLAN, "configuration").getData())
        .containsExactlyInAnyOrder(TerraformCommandFlagType.INIT, TerraformCommandFlagType.WORKSPACE,
            TerraformCommandFlagType.REFRESH, TerraformCommandFlagType.PLAN);
    assertThat(terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.APPLY, "configuration").getData())
        .containsExactlyInAnyOrder(TerraformCommandFlagType.INIT, TerraformCommandFlagType.WORKSPACE,
            TerraformCommandFlagType.REFRESH, TerraformCommandFlagType.PLAN, TerraformCommandFlagType.APPLY);
    assertThat(
        terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.DESTROY, "configuration").getData())
        .containsExactlyInAnyOrder(TerraformCommandFlagType.INIT, TerraformCommandFlagType.WORKSPACE,
            TerraformCommandFlagType.REFRESH, TerraformCommandFlagType.APPLY, TerraformCommandFlagType.DESTROY);
    assertThat(
        terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.ROLLBACK, "configuration").getData())
        .containsExactlyInAnyOrder(TerraformCommandFlagType.INIT, TerraformCommandFlagType.WORKSPACE,
            TerraformCommandFlagType.REFRESH, TerraformCommandFlagType.APPLY, TerraformCommandFlagType.DESTROY);
    assertThat(
        terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.PLAN, "cloudCliConfiguration").getData())
        .containsExactlyInAnyOrder(TerraformCommandFlagType.INIT, TerraformCommandFlagType.PLAN);
    assertThat(terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.APPLY, "cloudCliConfiguration")
                   .getData())
        .containsExactlyInAnyOrder(
            TerraformCommandFlagType.INIT, TerraformCommandFlagType.PLAN, TerraformCommandFlagType.APPLY);
    assertThat(terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.DESTROY, "cloudCliConfiguration")
                   .getData())
        .containsExactlyInAnyOrder(
            TerraformCommandFlagType.INIT, TerraformCommandFlagType.APPLY, TerraformCommandFlagType.DESTROY);
    assertThat(terraformResource.getTerraformCommandFlags(TerraformStepsForCliOptions.ROLLBACK, "cloudCliConfiguration")
                   .getData())
        .containsExactlyInAnyOrder(
            TerraformCommandFlagType.INIT, TerraformCommandFlagType.APPLY, TerraformCommandFlagType.DESTROY);
  }
}
