/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.resources;

import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_EDIT_PERMISSION;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.StringVariable;
import io.harness.ng.core.variable.entity.Variable;
import io.harness.ng.core.variable.mappers.VariableMapper;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class VariableResourceImplTest extends CategoryTest {
  @Mock private VariableService variableService;
  @Mock private VariableMapper variableMapper;
  @Mock private AccessControlClient accessControlClient;
  private VariableResource variableResource;

  @Captor ArgumentCaptor<String> stringArgumentCaptor;
  @Captor ArgumentCaptor<VariableDTO> variableDTOArgumentCaptor;
  @Captor ArgumentCaptor<Variable> variableArgumentCaptor;
  @Captor ArgumentCaptor<String> permissionArgumentCaptor;
  @Rule public ExpectedException expectedExceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    variableResource = new VariableResourceImpl(variableService, variableMapper, accessControlClient);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate() {
    String accountIdentifier = randomAlphabetic(10);
    VariableDTO variableDTO = VariableDTO.builder().build();
    VariableRequestDTO variableRequestDTO = VariableRequestDTO.builder().variable(variableDTO).build();
    StringVariable variable = StringVariable.builder().build();
    when(variableService.create(anyString(), any())).thenReturn(variable);
    when(variableMapper.toResponseWrapper(any())).thenReturn(VariableResponseDTO.builder().build());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    variableResource.create(accountIdentifier, variableRequestDTO);

    verify(variableService, times(1)).create(stringArgumentCaptor.capture(), variableDTOArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo(accountIdentifier);
    assertThat(variableDTOArgumentCaptor.getValue()).isEqualTo(variableDTO);

    verify(variableMapper, times(1)).toResponseWrapper(variableArgumentCaptor.capture());
    assertThat(variableArgumentCaptor.getValue()).isEqualTo(variable);

    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), permissionArgumentCaptor.capture());
    assertThat(permissionArgumentCaptor.getValue()).isEqualTo(VARIABLE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    when(variableService.get(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.ofNullable(VariableResponseDTO.builder().build()));
    ResponseDTO<VariableResponseDTO> returnVal =
        variableResource.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier);
    assertThat(returnVal).isNotNull();
    verify(variableService, times(1)).get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGet_notFoundException() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    when(variableService.get(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    expectedExceptionRule.expect(NotFoundException.class);
    expectedExceptionRule.expectMessage(
        String.format("Variable with identifier [%s] in project [%s] and org [%s] not found", identifier,
            projectIdentifier, orgIdentifier));
    variableResource.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier);
    verify(variableService, times(1)).get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    PageRequest pageRequest =
        PageRequest.builder()
            .pageIndex(0)
            .pageSize(10)
            .sortOrders(
                List.of(SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
            .build();
    when(variableService.list(
             accountIdentifier, orgIdentifier, projectIdentifier, null, false, getPageRequest(pageRequest)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder().build());
    ResponseDTO<PageResponse<VariableResponseDTO>> list =
        variableResource.list(accountIdentifier, orgIdentifier, projectIdentifier, null, false, pageRequest);
    assertThat(list).isNotNull();
    verify(variableService, times(1))
        .list(accountIdentifier, orgIdentifier, projectIdentifier, null, false, getPageRequest(pageRequest));
  }
}
