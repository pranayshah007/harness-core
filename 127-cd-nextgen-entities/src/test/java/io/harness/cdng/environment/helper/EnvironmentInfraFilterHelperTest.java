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
import io.harness.cdng.environment.filters.AllowAllFilter;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.MatchType;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class EnvironmentInfraFilterHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  @Parameters(method = "getFilters")
  public void testProcessTagsFilterYamlForEnvironmentsForMatchAll(FilterYaml input) {
    EnvironmentInfraFilterHelper environmentInfraFilterHelper = new EnvironmentInfraFilterHelper();
    List<NGTag> envTags = Arrays.asList(NGTag.builder().key("env").value("dev").build());

    List<Environment> listOfEnvironment = Arrays.asList(Environment.builder().tags(envTags).build());

    List<Environment> filteredEnv =
        environmentInfraFilterHelper.processTagsFilterYamlForEnvironments(input, listOfEnvironment);
    assertThat(listOfEnvironment.size()).isEqualTo(filteredEnv.size());
  }
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  @Parameters(method = "getFilters")
  public void testProcessTagsFilterYamlForEnvironmentsForMatchAny(FilterYaml filterYaml) {
    EnvironmentInfraFilterHelper environmentInfraFilterHelper = new EnvironmentInfraFilterHelper();
    List<NGTag> envTags = Arrays.asList(NGTag.builder().key("env").value("dev").build());
    List<Environment> listOfEnvironment = Arrays.asList(Environment.builder().tags(envTags).build());

    List<Environment> filteredEnv =
        environmentInfraFilterHelper.processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(listOfEnvironment.size()).isEqualTo(filteredEnv.size());
  }

  private Object[][] getFilters() {
    Map<String, String> mapOfTags = Map.of("env", "dev", "env1", "dev1");
    final FilterYaml filter1 = FilterYaml.builder()
                                   .entities(Set.of(Entity.environments))
                                   .type(FilterType.tags)
                                   .spec(TagsFilter.builder().matchType(MatchType.any).tags(mapOfTags).build())
                                   .build();

    final FilterYaml filter2 = FilterYaml.builder()
                                   .entities(Set.of(Entity.environments))
                                   .type(FilterType.tags)
                                   .spec(TagsFilter.builder().matchType(MatchType.all).tags(mapOfTags).build())
                                   .build();

    final FilterYaml filter3 = FilterYaml.builder()
                                   .entities(Set.of(Entity.environments))
                                   .type(FilterType.all)
                                   .spec(AllowAllFilter.builder().build())
                                   .build();

    return new Object[][] {{filter1}, {filter2}, {filter3}};
  }
}