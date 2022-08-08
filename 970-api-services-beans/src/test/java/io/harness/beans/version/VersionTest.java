/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.version;

import static io.harness.rule.OwnerRule.BOGDAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class VersionTest {
  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void testComparableSort() {
    // given
    List<String> unsorted = Arrays.asList("1.0.2", "1.0.0-beta.2", "1.0.0", "1.0.0-beta", "1.0.0-alpha.12",
        "1.0.0-beta.11", "1.0.1", "1.0.11", "1.0.0-rc.1", "1.0.0-alpha.1", "1.1.0", "1.0.0-alpha.beta", "1.11.0",
        "1.0.0-alpha.12.ab-c", "0.0.1", "1.2.1", "1.0.0-alpha", "1.0.0.1", "1.0.0.2", "2", "10", "1.0.0.10");

    List<String> sortedExpected = Arrays.asList("0.0.1", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.12",
        "1.0.0-alpha.12.ab-c", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0",
        "1.0.0.1", "1.0.0.2", "1.0.0.10", "1.0.1", "1.0.2", "1.0.11", "1.1.0", "1.2.1", "1.11.0", "2", "10");

    // given
    List<Version> sortedActual = unsorted.stream().map(Version::parse).sorted().collect(Collectors.toList());
    List<Version> sortedExpecedVersion = sortedExpected.stream().map(Version::parse).collect(Collectors.toList());

    // then
    assertThat(sortedActual).isEqualTo(sortedExpecedVersion);
  }
}
