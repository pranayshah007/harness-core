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
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.MatchType;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentInfraFilterHelperTest extends CategoryTest {
  EnvironmentInfraFilterHelper environmentInfraFilterHelper = new EnvironmentInfraFilterHelper();

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  void processTagsFilterYamlForEnvironmentsForMatchAll() {
    List<NGTag> envTags = Arrays.asList(NGTag.builder().key("env").value("dev").build());

    List<Environment> listOfEnvironment = Arrays.asList(Environment.builder().tags(envTags).build());

    List<NGTag> listOfTags = Arrays.asList(NGTag.builder().key("env").value("dev").build());
    // Match All Filter Yaml
    FilterYaml filterYaml = FilterYaml.builder()
                                .entities(Set.of(Entity.environments))
                                .spec(TagsFilter.builder().matchType(MatchType.all).tags(listOfTags).build())

                                .build();
    List<Environment> filteredEnv =
        environmentInfraFilterHelper.processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(listOfEnvironment.size()).isEqualTo(filteredEnv.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  void processTagsFilterYamlForEnvironmentsForMatchAny() {
    List<NGTag> envTags = Arrays.asList(NGTag.builder().key("env").value("dev").build());

    List<Environment> listOfEnvironment = Arrays.asList(Environment.builder().tags(envTags).build());

    List<NGTag> listOfTags = Arrays.asList(
        NGTag.builder().key("env").value("dev").build(), NGTag.builder().key("env1").value("dev1").build());
    // Match All Filter Yaml
    FilterYaml filterYaml = FilterYaml.builder()
                                .entities(Set.of(Entity.environments))
                                .spec(TagsFilter.builder().matchType(MatchType.any).tags(listOfTags).build())

                                .build();
    List<Environment> filteredEnv =
        environmentInfraFilterHelper.processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(listOfEnvironment.size()).isEqualTo(filteredEnv.size());
  }
}