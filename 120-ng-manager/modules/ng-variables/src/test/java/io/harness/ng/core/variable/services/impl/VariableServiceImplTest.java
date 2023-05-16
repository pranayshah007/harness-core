/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services.impl;

import static io.harness.ng.core.variable.VariableType.STRING;
import static io.harness.ng.core.variable.VariableValueType.FIXED;
import static io.harness.ng.core.variable.VariableValueType.FIXED_SET;
import static io.harness.ng.core.variable.VariableValueType.REGEX;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.utils.PageTestUtils.getPage;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.events.VariableCreateEvent;
import io.harness.ng.core.events.VariableUpdateEvent;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.variable.VariableValueType;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO.StringVariableConfigDTOKeys;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.StringVariable;
import io.harness.ng.core.variable.entity.Variable;
import io.harness.ng.core.variable.mappers.VariableMapper;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.variable.spring.VariableRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class VariableServiceImplTest extends CategoryTest {
  @Mock private VariableRepository variableRepository;
  @Mock private VariableMapper variableMapper;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;
  @Mock private OrganizationService organizationService;
  @Mock private ProjectService projectService;
  @Mock private Project project;
  @Mock Organization organization;
  private VariableServiceImpl variableService;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  @Captor private ArgumentCaptor<VariableCreateEvent> variableCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<VariableUpdateEvent> variableUpdateEventArgumentCaptor;
  PageRequest pageRequest;
  Pageable pageable;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    pageRequest = PageRequest.builder()
                      .pageIndex(0)
                      .pageSize(10)
                      .sortOrders(List.of(
                          SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
                      .build();
    pageable = getPageRequest(pageRequest);
    this.variableService = new VariableServiceImpl(
        variableRepository, variableMapper, transactionTemplate, outboxService, projectService, organizationService);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringFixedValueMissingValue() {
    VariableDTO variableDTO = VariableDTO.builder()
                                  .type(STRING)
                                  .variableConfig(StringVariableConfigDTO.builder().valueType(FIXED).build())
                                  .build();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("Value for field [%s] must be provide when value type is [%s]",
        StringVariableConfigDTOKeys.fixedValue, VariableValueType.FIXED));
    variableDTO.getVariableConfig().validate();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringFixedSetMissingAllowedValues() {
    VariableDTO variableDTO = VariableDTO.builder()
                                  .type(STRING)
                                  .variableConfig(StringVariableConfigDTO.builder().valueType(FIXED_SET).build())
                                  .build();
    exceptionRule.expect(UnsupportedOperationException.class);
    exceptionRule.expectMessage(
        String.format("Value Type [%s] is not supported", variableDTO.getVariableConfig().getValueType().name()));
    variableDTO.getVariableConfig().validate();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringRegexMissingRegex() {
    VariableDTO variableDTO = VariableDTO.builder()
                                  .type(STRING)
                                  .variableConfig(StringVariableConfigDTO.builder().valueType(REGEX).build())
                                  .build();
    exceptionRule.expect(UnsupportedOperationException.class);
    exceptionRule.expectMessage(
        String.format("Value Type [%s] is not supported", variableDTO.getVariableConfig().getValueType().name()));
    variableDTO.getVariableConfig().validate();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateValidateDTO_stringRegexInvalidRegex() {
    String regex = "[a-z]\\i";
    VariableDTO variableDTO =
        VariableDTO.builder()
            .type(STRING)
            .variableConfig(StringVariableConfigDTO.builder().valueType(REGEX).regex(regex).build())
            .build();
    exceptionRule.expect(UnsupportedOperationException.class);
    exceptionRule.expectMessage(
        String.format("Value Type [%s] is not supported", variableDTO.getVariableConfig().getValueType().name()));
    variableDTO.getVariableConfig().validate();
  }

  private VariableDTO getVariableDTO(String identifier, String orgIdentifier, String projectIdentifier, String value) {
    return VariableDTO.builder()
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .variableConfig(StringVariableConfigDTO.builder().valueType(FIXED).fixedValue(value).build())
        .build();
  }

  private VariableResponseDTO getVariableResponseDTO(
      String identifier, String orgIdentifier, String projectIdentifier, String value) {
    return VariableResponseDTO.builder()
        .variable(getVariableDTO(identifier, orgIdentifier, projectIdentifier, value))
        .build();
  }

  private Variable getVariable(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String value) {
    Variable variable = StringVariable.builder().fixedValue(value).build();
    variable.setAccountIdentifier(accountIdentifier);
    variable.setOrgIdentifier(orgIdentifier);
    variable.setProjectIdentifier(projectIdentifier);
    variable.setIdentifier(identifier);
    variable.setValueType(FIXED);
    variable.setType(STRING);
    return variable;
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);

    when(variableMapper.toVariable(accountIdentifier, variableDTO)).thenReturn(variable);
    when(variableMapper.writeDTO(variable)).thenReturn(variableDTO);
    when(variableRepository.save(variable)).thenReturn(variable);
    when(projectService.get(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(Optional.of(project));
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    variableService.create(accountIdentifier, variableDTO);
    verify(variableMapper, times(1)).toVariable(accountIdentifier, variableDTO);
    verify(transactionTemplate, times(1)).execute(any());
    verify(variableRepository, times(1)).save(variable);
    verify(outboxService, times(1)).save(variableCreateEventArgumentCaptor.capture());
    VariableCreateEvent capturedVariableCreateEvent = variableCreateEventArgumentCaptor.getValue();
    assertThat(variableDTO).isEqualTo(capturedVariableCreateEvent.getVariableDTO());
  }

  @Test(expected = DuplicateFieldException.class)
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate_duplicateKeyException() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableMapper.toVariable(accountIdentifier, variableDTO)).thenReturn(variable);
    when(variableRepository.save(variable)).thenThrow(new DuplicateKeyException(""));
    when(projectService.get(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(Optional.of(project));
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    variableService.create(accountIdentifier, variableDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate_invalidRequestException() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    VariableDTO variableDTO = VariableDTO.builder()
                                  .identifier(identifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .build();
    variableService.create(accountIdentifier, variableDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreate_ProjectWithoutOrg() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, null, projectIdentifier, value);
    Variable variable = getVariable(accountIdentifier, null, projectIdentifier, identifier, value);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("Project %s specified without the org Identifier", projectIdentifier));
    variableService.create(accountIdentifier, variableDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String var1 = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, var1, var1);
    VariableDTO varADTO = getVariableDTO(var1, orgIdentifier, projectIdentifier, var1);
    List<Variable> variables = new ArrayList<>(Collections.singletonList(varA));
    when(variableRepository.findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
             anyString(), anyString(), anyString()))
        .thenReturn(variables);
    when(variableMapper.writeDTO(varA)).thenReturn(varADTO);
    List<VariableDTO> varList = variableService.list(accountIdentifier, orgIdentifier, projectIdentifier);
    verify(variableRepository, times(1))
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier);
    assertThat(varList).hasOnlyElementsOfType(VariableDTO.class);
    assertThat(varList.size()).isEqualTo(variables.size());
    assertThat(varList).contains(varADTO);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForAccountScope() {
    String accountIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, null, null, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varList.add(varA);
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, null, null, varID);
    when(variableRepository.findAll(any(), any())).thenReturn(getPage(varList, 1));
    when(variableMapper.toResponseWrapper(varA)).thenReturn(variableResponseDTO);
    PageResponse<VariableResponseDTO> list = variableService.list(accountIdentifier, null, null, null, false, pageable);
    verify(variableRepository, times(1)).findAll(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForOrgScope() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, orgIdentifier, null, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varList.add(varA);
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, orgIdentifier, null, varID);
    when(variableMapper.toResponseWrapper(varA)).thenReturn(variableResponseDTO);
    when(variableRepository.findAll(any(), any())).thenReturn(getPage(varList, 1));
    PageResponse<VariableResponseDTO> list =
        variableService.list(accountIdentifier, orgIdentifier, null, null, false, pageable);
    verify(variableRepository, times(1)).findAll(any(), any());
    System.out.println(list.getContent().get(0));
    assertThat(list.getContent().size()).isEqualTo(varList.size());
    assertThat(list.getContent().get(0).getVariable().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(list.getContent().get(0).getVariable().getProjectIdentifier()).isEqualTo(null);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForProjectScope() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varList.add(varA);
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, orgIdentifier, projectIdentifier, varID);
    when(variableMapper.toResponseWrapper(varA)).thenReturn(variableResponseDTO);
    when(variableRepository.findAll(any(), any())).thenReturn(getPage(varList, 1));
    PageResponse<VariableResponseDTO> list =
        variableService.list(accountIdentifier, orgIdentifier, projectIdentifier, null, false, pageable);
    verify(variableRepository, times(1)).findAll(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
    assertThat(list.getContent().get(0).getVariable().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(list.getContent().get(0).getVariable().getProjectIdentifier()).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testPagedListForProjectScopeWithSearchTerm() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String varID = randomAlphabetic(5);
    Variable varA = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, varID, varID);
    List<Variable> varList = new ArrayList<>();
    varList.add(varA);
    VariableResponseDTO variableResponseDTO = getVariableResponseDTO(varID, orgIdentifier, projectIdentifier, varID);
    when(variableMapper.toResponseWrapper(varA)).thenReturn(variableResponseDTO);
    when(variableRepository.findAll(any(), any())).thenReturn(getPage(varList, 1));
    PageResponse<VariableResponseDTO> list =
        variableService.list(accountIdentifier, orgIdentifier, projectIdentifier, varID, false, pageable);
    verify(variableRepository, times(1)).findAll(any(), any());
    assertThat(list.getContent().size()).isEqualTo(varList.size());
    assertThat(list.getContent().get(0).getVariable().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(list.getContent().get(0).getVariable().getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(list.getContent().get(0).getVariable().getIdentifier()).isEqualTo(varID);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(5);
    String value = randomAlphabetic(7);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    VariableResponseDTO variableResponseDTO =
        getVariableResponseDTO(identifier, orgIdentifier, projectIdentifier, value);
    when(variableRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(variable));
    when(variableMapper.toResponseWrapper(variable)).thenReturn(variableResponseDTO);
    assertThat(variableService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier)).isNotNull();
    verify(variableRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    verify(variableMapper, times(1)).toResponseWrapper(variable);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdate() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    variableDTO.setType(STRING);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableMapper.toVariable(accountIdentifier, variableDTO)).thenReturn(variable);
    when(variableMapper.writeDTO(variable)).thenReturn(variableDTO);
    when(variableRepository.save(variable)).thenReturn(variable);
    when(variableRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(Optional.of(variable));
    when(projectService.get(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(Optional.of(project));
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    variableService.update(accountIdentifier, variableDTO);
    verify(projectService, times(1)).get(accountIdentifier, orgIdentifier, projectIdentifier);
    verify(variableMapper, times(1)).toVariable(accountIdentifier, variableDTO);
    verify(transactionTemplate, times(1)).execute(any());
    verify(variableRepository, times(1)).save(variable);
    verify(outboxService, times(1)).save(variableUpdateEventArgumentCaptor.capture());
    VariableUpdateEvent capturedVariableUpdateEvent = variableUpdateEventArgumentCaptor.getValue();
    assertThat(variableDTO).isEqualTo(capturedVariableUpdateEvent.getNewVariableDTO());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdate_changeValueType() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, orgIdentifier, projectIdentifier, value);
    variableDTO.setType(STRING);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    Variable variable_edited = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    variable_edited.setValueType(REGEX);

    when(variableRepository.save(variable)).thenReturn(variable);
    when(variableRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(Optional.of(variable_edited));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Variable Value Type cannot be changed");
    variableService.update(accountIdentifier, variableDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdate_ProjectWithoutOrg() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    VariableDTO variableDTO = getVariableDTO(identifier, null, projectIdentifier, value);
    variableDTO.setType(STRING);
    Variable variable = getVariable(accountIdentifier, null, projectIdentifier, identifier, value);

    when(variableRepository.save(variable)).thenReturn(variable);
    when(variableRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, null, projectIdentifier, identifier))
        .thenReturn(Optional.of(variable));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("Project %s specified without the org Identifier", projectIdentifier));
    variableService.update(accountIdentifier, variableDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void tesDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(5);
    String value = randomAlphabetic(7);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(variable));
    variableService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    verify(variableRepository, times(1)).delete(variable);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void tesDelete_VariableNotExists() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(5);
    String value = randomAlphabetic(7);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    exceptionRule.expect(NotFoundException.class);
    variableService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteBatch() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    List<String> variableIdentifiers = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      variableIdentifiers.add(randomAlphabetic(10));
    }
    String identifier = randomAlphabetic(5);
    String value = randomAlphabetic(7);
    Variable variable = getVariable(accountIdentifier, orgIdentifier, projectIdentifier, identifier, value);
    when(variableRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(variable));
    variableService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier, variableIdentifiers);
    verify(variableRepository, times(5)).delete(variable);
  }
}
