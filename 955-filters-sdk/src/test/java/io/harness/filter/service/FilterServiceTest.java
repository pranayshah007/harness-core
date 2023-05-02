/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filter.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersTestBase;
import io.harness.filter.dto.FilterDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FilterServiceTest extends FiltersTestBase {
  @Inject FilterService filterService;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String filterIdentifier = "filterIdentifier";

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testGet() {
    FilterDTO inputFilterDTO = createFilter(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier);
    FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    assertThat(savedFilterDTO).isNotNull();
    verifyTheValuesAreCorrect(inputFilterDTO, savedFilterDTO);
  }

  private void verifyTheValuesAreCorrect(FilterDTO inputFilterDTO, FilterDTO savedFilterDTO) {
    assertThat(savedFilterDTO.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(savedFilterDTO.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(savedFilterDTO.getIdentifier()).isEqualTo(filterIdentifier);
    assertThat(savedFilterDTO.getName()).isEqualTo(inputFilterDTO.getName());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)

  public void testListForAccountLevelFilter() {
    for (int i = 0; i < 5; i++) {
      FilterDTO inputFilterDTO = createFilter(accountIdentifier, null, null, filterIdentifier);
      FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    }
    FilterDTO inputFilterDTO = createFilter(accountIdentifier, orgIdentifier, null, filterIdentifier);
    FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    List<FilterDTO> filtersList =
        filterService.list(0, 100, accountIdentifier, null, null, null, FilterType.CONNECTOR).getContent();
    assertThat(filtersList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)

  public void testListForOrgLevelFilter() {
    for (int i = 0; i < 5; i++) {
      FilterDTO inputFilterDTO = createFilter(accountIdentifier, orgIdentifier, null, filterIdentifier);
      FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    }
    FilterDTO inputFilterDTO = createFilter(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier);
    FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    List<FilterDTO> filtersList =
        filterService.list(0, 100, accountIdentifier, orgIdentifier, null, null, FilterType.CONNECTOR).getContent();
    assertThat(filtersList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)

  public void testListForProjectLevelFilter() {
    for (int i = 0; i < 5; i++) {
      FilterDTO inputFilterDTO = createFilter(accountIdentifier, null, null, filterIdentifier);
      FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    }
    FilterDTO inputFilterDTO = createFilter(accountIdentifier, orgIdentifier, null, filterIdentifier);
    FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    List<FilterDTO> filtersList =
        filterService.list(0, 100, accountIdentifier, null, null, null, FilterType.CONNECTOR).getContent();
    assertThat(filtersList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreate() {
    FilterDTO inputFilterDTO = createFilter(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier);
    FilterDTO savedFilterDTO = filterService.create(accountIdentifier, inputFilterDTO);
    assertThat(savedFilterDTO).isNotNull();
    verifyTheValuesAreCorrect(inputFilterDTO, savedFilterDTO);
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeleteWhenFilterDoesnotExists() {
    filterService.delete(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.CONNECTOR);
  }

  public FilterDTO createFilter(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return FilterDTO.builder()
        .name("name")
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(identifier)
        .build();
  }
}
