/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filter.resource;

import static io.harness.filter.FilterType.CONNECTOR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PageTestUtils;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;

public class FilterResourceTest extends CategoryTest {
  @InjectMocks FilterResource filterResource;
  @Mock FilterService filterService;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";
  private FilterDTO filterDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    filterDTO = FilterDTO.builder().build();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testGet() {
    when(filterService.get(any(), any(), any(), any(), any())).thenReturn(filterDTO);
    FilterDTO connectorFilterDTOOutput =
        filterResource.get("accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", CONNECTOR)
            .getData();
    Mockito.verify(filterService, times(1)).get(any(), any(), any(), any(), any());
    assertThat(connectorFilterDTOOutput).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testList() {
    final Page<FilterDTO> page = PageTestUtils.getPage(Arrays.asList(FilterDTO.builder().build()), 1);
    when(filterService.list(0, 100, accountIdentifier, orgIdentifier, projectIdentifier, null, CONNECTOR))
        .thenReturn(page);
    ResponseDTO<PageResponse<FilterDTO>> connectorFilterList =
        filterResource.list(0, 100, accountIdentifier, orgIdentifier, projectIdentifier, CONNECTOR);
    Mockito.verify(filterService, times(1))
        .list(eq(0), eq(100), eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(null), eq(CONNECTOR));
    assertThat(connectorFilterList.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreate() {
    doReturn(FilterDTO.builder().build()).when(filterService).create(any(), any());
    ResponseDTO<FilterDTO> connectorFilterOutput =
        filterResource.create(FilterDTO.builder().build(), accountIdentifier);
    Mockito.verify(filterService, times(1)).create(any(), any());
    assertThat(connectorFilterOutput.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testUpdate() {
    doReturn(FilterDTO.builder().build()).when(filterService).update(any(), any());
    ResponseDTO<FilterDTO> connectorFilterOutput =
        filterResource.update(FilterDTO.builder().build(), accountIdentifier);
    Mockito.verify(filterService, times(1)).update(any(), any());
    assertThat(connectorFilterOutput.getData()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDelete() {
    when(filterService.delete(any(), any(), any(), any(), any())).thenReturn(true);
    ResponseDTO<Boolean> result = filterResource.delete(
        "accountIdentifier", "orgIdentifier", "projectIdentifier", "connectorIdentifier", CONNECTOR);
    Mockito.verify(filterService, times(1)).delete(any(), any(), any(), any(), any());
    assertThat(result.getData()).isTrue();
  }
}
