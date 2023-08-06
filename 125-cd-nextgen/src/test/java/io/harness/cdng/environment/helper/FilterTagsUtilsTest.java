/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.filters.MatchType;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.GITOPS)
public class FilterTagsUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testAreAllTagFiltersMatching() {
    NGTag t1 = NGTag.builder().key("k1").value("v1").build();
    NGTag t2 = NGTag.builder().key("k2").value("v2").build();
    NGTag t3 = NGTag.builder().key("k3").value("v3").build();
    NGTag t4 = NGTag.builder().key("k4").value("v4").build();
    NGTag t5 = NGTag.builder().key("k1").value("v1").build();

    assertThat(FilterTagsUtils.areAllTagFiltersMatching(null, null)).isFalse();
    assertThat(FilterTagsUtils.areAllTagFiltersMatching(Arrays.asList(t1, t2), Collections.emptyList())).isFalse();
    assertThat(FilterTagsUtils.areAllTagFiltersMatching(Collections.emptyList(), Arrays.asList(t1, t2))).isFalse();
    assertThat(FilterTagsUtils.areAllTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t1, t2))).isTrue();
    assertThat(FilterTagsUtils.areAllTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t1, t2, t4)))
        .isFalse();
    assertThat(FilterTagsUtils.areAllTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t5, t2))).isTrue();
    assertThat(FilterTagsUtils.areAllTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t4))).isFalse();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testAreAnyTagFiltersMatching() {
    NGTag t1 = NGTag.builder().key("k1").value("v1").build();
    NGTag t2 = NGTag.builder().key("k2").value("v2").build();
    NGTag t3 = NGTag.builder().key("k3").value("v3").build();
    NGTag t4 = NGTag.builder().key("k4").value("v4").build();
    NGTag t5 = NGTag.builder().key("k1").value("v1").build();

    assertThat(FilterTagsUtils.areAnyTagFiltersMatching(null, null)).isFalse();
    assertThat(FilterTagsUtils.areAnyTagFiltersMatching(Arrays.asList(t1, t2), Collections.emptyList())).isFalse();
    assertThat(FilterTagsUtils.areAnyTagFiltersMatching(Collections.emptyList(), Arrays.asList(t1, t2))).isFalse();
    assertThat(FilterTagsUtils.areAnyTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t1, t2))).isTrue();
    assertThat(FilterTagsUtils.areAnyTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t1, t2, t4))).isTrue();
    assertThat(FilterTagsUtils.areAnyTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t5))).isTrue();
    assertThat(FilterTagsUtils.areAnyTagFiltersMatching(Arrays.asList(t1, t2, t3), Arrays.asList(t4))).isFalse();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testAreTagsFilterMatching() {
    NGTag t1 = NGTag.builder().key("k1").value("v1").build();
    NGTag t2 = NGTag.builder().key("k2").value("v2").build();
    NGTag t3 = NGTag.builder().key("k3").value("v3").build();
    NGTag t4 = NGTag.builder().key("k4").value("v4").build();

    NGTag t5 = NGTag.builder().key("k1").value("v1").build();

    TagsFilter anyTagsFilter = TagsFilter.builder()
                                   .matchType(ParameterField.createValueField(MatchType.any.name()))
                                   .tags(ParameterField.createValueField(TagMapper.convertToMap(Arrays.asList(t4, t5))))
                                   .build();

    TagsFilter allTagsFilter = TagsFilter.builder()
                                   .matchType(ParameterField.createValueField(MatchType.all.name()))
                                   .tags(ParameterField.createValueField(TagMapper.convertToMap(Arrays.asList(t4, t5))))
                                   .build();

    TagsFilter wrongTagsFilter =
        TagsFilter.builder()
            .matchType(ParameterField.createValueField("wrong"))
            .tags(ParameterField.createValueField(TagMapper.convertToMap(Arrays.asList(t4, t5))))
            .build();

    assertThat(FilterTagsUtils.areTagsFilterMatching(Arrays.asList(t1, t2, t3), anyTagsFilter)).isTrue();
    assertThat(FilterTagsUtils.areTagsFilterMatching(Arrays.asList(t1, t2, t3), allTagsFilter)).isFalse();
    Assertions
        .assertThatThrownBy(() -> FilterTagsUtils.areTagsFilterMatching(Arrays.asList(t1, t2, t3), wrongTagsFilter))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("TagFilter of type [wrong] is not supported");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testAreTagsFilterMatchingWithInvalidTagValue() {
    NGTag t1 = NGTag.builder().key("k1").value("v1").build();

    TagsFilter anyTagsFilter = TagsFilter.builder()
                                   .matchType(ParameterField.createValueField(MatchType.any.name()))
                                   .tags(ParameterField.createValueField(null))
                                   .build();
    TagsFilter anyTagsFilter2 = TagsFilter.builder()
                                    .matchType(ParameterField.createValueField(MatchType.any.name()))
                                    .tags(ParameterField.createExpressionField(true, "<+abcd>", null, false))
                                    .build();

    Assertions.assertThatThrownBy(() -> FilterTagsUtils.areTagsFilterMatching(Arrays.asList(t1), anyTagsFilter))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format(
            "Invalid filter tags value found [%s]. Filter tags should be non-empty key-value pairs of string values.",
            null));

    Assertions.assertThatThrownBy(() -> FilterTagsUtils.areTagsFilterMatching(Arrays.asList(t1), anyTagsFilter2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format(
            "Invalid filter tags value found [%s]. Filter tags should be non-empty key-value pairs of string values.",
            null));
  }
}
