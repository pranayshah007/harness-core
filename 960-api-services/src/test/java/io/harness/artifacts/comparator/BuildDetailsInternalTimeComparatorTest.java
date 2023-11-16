/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.comparator;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BuildDetailsInternalTimeComparatorTest extends CategoryTest {
  private BuildDetailsInternal.BuildDetailsInternalBuilder buildDetailsInternalBuilder =
      BuildDetailsInternal.builder().buildUrl("URL").uiDisplayName("DISPLAY_NAME");

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testShouldSortBuildDetailsInternalAscending() throws ParseException {
    List<BuildDetailsInternal> buildDetailsInternalList =
        asList(buildDetailsInternalBuilder.number("todolist-1.0-1.x86_64.rpm")
                   .imagePushedAt(parseDate("2023-02-02T16:48:55-08:00"))
                   .build(),
            buildDetailsInternalBuilder.number("todolist-1.0-10.x86_64.rpm")
                .imagePushedAt(parseDate("2023-03-02T16:48:55-08:00"))
                .build(),
            buildDetailsInternalBuilder.number("todolist-1.0-5.x86_64.rpm")
                .imagePushedAt(parseDate("2023-03-02T16:48:55-08:00"))
                .build(),
            buildDetailsInternalBuilder.number("todolist-1.0-15.x86_64.rpm")
                .imagePushedAt(parseDate("2023-02-02T16:48:55-08:00"))
                .build());
    assertThat(
        buildDetailsInternalList.stream().sorted(new BuildDetailsInternalTimeComparator()).collect(Collectors.toList()))
        .hasSize(4)
        .extracting(BuildDetailsInternal::getNumber)
        .containsSequence("todolist-1.0-10.x86_64.rpm", "todolist-1.0-5.x86_64.rpm", "todolist-1.0-15.x86_64.rpm",
            "todolist-1.0-1.x86_64.rpm");
  }

  private static Date parseDate(String date) throws ParseException {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    return sdf.parse(date);
  }
}
