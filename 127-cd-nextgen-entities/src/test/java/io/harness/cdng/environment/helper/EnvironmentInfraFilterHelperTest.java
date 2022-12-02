/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.MatchType;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentInfraFilterHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForEnvironmentsForMatchAll() {
    EnvironmentInfraFilterHelper environmentInfraFilterHelper = new EnvironmentInfraFilterHelper();
    Set<Environment> listOfEnvironment = getEnvironmentListForAllTagMatch();
    Map<String, String> mapOfTags = Map.of("env", "dev");
    final FilterYaml filterYaml = FilterYaml.builder()
                                      .entities(Set.of(Entity.environments))
                                      .type(FilterType.tags)
                                      .spec(TagsFilter.builder().matchType(MatchType.all).tags(mapOfTags).build())
                                      .build();

    Set<Environment> filteredEnv =
        environmentInfraFilterHelper.processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(filteredEnv.size()).isEqualTo(1);
  }
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForEnvironmentsForMatchAny() {
    EnvironmentInfraFilterHelper environmentInfraFilterHelper = new EnvironmentInfraFilterHelper();
    Set<Environment> listOfEnvironment = getEnvironmentListForAnyTagMatch();

    Map<String, String> mapOfTags = Map.of("env", "dev", "env1", "dev1");
    final FilterYaml filterYaml = FilterYaml.builder()
                                      .entities(Set.of(Entity.environments))
                                      .type(FilterType.tags)
                                      .spec(TagsFilter.builder().matchType(MatchType.any).tags(mapOfTags).build())
                                      .build();

    Set<Environment> filteredEnv =
        environmentInfraFilterHelper.processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(listOfEnvironment.size()).isEqualTo(filteredEnv.size());
  }

  @NotNull
  private static Set<Environment> getEnvironmentListForAnyTagMatch() {
    List<NGTag> env1Tags = Arrays.asList(NGTag.builder().key("env").value("dev").build());
    List<NGTag> env2Tags = Arrays.asList(
        NGTag.builder().key("env").value("dev").build(), NGTag.builder().key("env1").value("dev2").build());
    final Set<Environment> listOfEnvironment = new HashSet<>(
        Arrays.asList(Environment.builder().tags(env1Tags).build(), Environment.builder().tags(env2Tags).build()));
    return listOfEnvironment;
  }

  @NotNull
  private static Set<Environment> getEnvironmentListForAllTagMatch() {
    List<NGTag> env1Tags = Arrays.asList(NGTag.builder().key("env").value("dev").build());
    List<NGTag> env2Tags = Arrays.asList(NGTag.builder().key("env1").value("dev2").build());
    final Set<Environment> listOfEnvironment = new HashSet<>(
        Arrays.asList(Environment.builder().tags(env1Tags).build(), Environment.builder().tags(env2Tags).build()));
    return listOfEnvironment;
  }
}