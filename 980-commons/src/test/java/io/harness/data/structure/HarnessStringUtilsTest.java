/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.structure;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HarnessStringUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldJoinIfIndividualElements() {
    String joinedString = HarnessStringUtils.join("/", "foo", "bar", "hello-world");
    assertThat(joinedString).isEqualTo("foo/bar/hello-world");

    joinedString = HarnessStringUtils.join(StringUtils.EMPTY, "foo", "bar", "hello-world");
    assertThat(joinedString).isEqualTo("foobarhello-world");
  }
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldJoinIfIterableElements() {
    String joinedString = HarnessStringUtils.join("/", Arrays.asList("foo", "bar", "hello-world"));
    assertThat(joinedString).isEqualTo("foo/bar/hello-world");

    joinedString = HarnessStringUtils.join(StringUtils.EMPTY,
        Arrays.asList("foo", "bar",
            "hello"
                + "-world"));
    assertThat(joinedString).isEqualTo("foobarhello-world");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRemoveLeadingAndTrailingQuotesBothOrNone() {
    // No quotes. So no change.
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("foo")).isEqualTo("foo");
    // ony leading quote. Will not be removed.
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("\"foo")).isEqualTo("\"foo");
    // ony trailing quote. Will not be removed.
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("foo\"")).isEqualTo("foo\"");
    // Leading and trailing quotes, will be removed.
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("\"foo\"")).isEqualTo("foo");
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("\"\"foo\"")).isEqualTo("\"foo");

    // quotes are not at start or end. No change.
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("f\"o\"o")).isEqualTo("f\"o\"o");
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("fo\"o")).isEqualTo("fo\"o");

    // Leading and trailing quotes 2 times, only one will be removed.
    assertThat(HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone("\"\"foo\"\"")).isEqualTo("\"foo\"");
  }
}
