/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class HelmFetchFileResultTest extends CategoryTest {
  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testAddAll() {
    HelmFetchFileResult helmFetchFileResult =
        HelmFetchFileResult.builder()
            .valuesFileContents(new ArrayList<>(Arrays.asList("Values file payload 2")))
            .build();
    HelmFetchFileResult newHelmFetchFileResult =
        HelmFetchFileResult.builder()
            .valuesFileContents(new ArrayList<>(Arrays.asList("Values file payload 1")))
            .build();
    newHelmFetchFileResult.addAllFrom(helmFetchFileResult);
    assertThat(newHelmFetchFileResult.getValuesFileContents().size()).isEqualTo(2);
    assertThat(newHelmFetchFileResult.getValuesFileContents().get(0)).isEqualTo("Values file payload 1");
    assertThat(newHelmFetchFileResult.getValuesFileContents().get(1)).isEqualTo("Values file payload 2");
  }
}
