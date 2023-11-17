/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromDTO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEvent;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDao;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationRequest;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationResult;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidator;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import io.serializer.HObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class RoleAssignmentServiceImplTest extends AccessControlCoreTestBase {
  private RoleAssignmentDao roleAssignmentDao;
  private RoleAssignmentValidator roleAssignmentValidator;
  private RoleAssignmentServiceImpl roleAssignmentService;
  private TransactionTemplate outboxTransactionTemplate;
  private OutboxService outboxService;
  private ScopeService scopeService;

  @Before
  public void setup() {
    roleAssignmentDao = mock(RoleAssignmentDao.class);
    roleAssignmentValidator = mock(RoleAssignmentValidator.class);
    outboxTransactionTemplate = mock(TransactionTemplate.class);
    outboxService = mock(OutboxService.class);
    scopeService = mock(ScopeService.class);
    roleAssignmentService = spy(new RoleAssignmentServiceImpl(
        roleAssignmentDao, roleAssignmentValidator, outboxTransactionTemplate, outboxService, scopeService));
  }

  private RoleAssignment getRoleAssignment(Scope scope) {
    return RoleAssignment.builder()
        .id(randomAlphabetic(10))
        .identifier(randomAlphabetic(10))
        .scopeIdentifier(scope.toString())
        .roleIdentifier(randomAlphabetic(10))
        .principalType(PrincipalType.USER)
        .principalIdentifier(randomAlphabetic(10))
        .principalScopeLevel(randomAlphabetic(10))
        .resourceGroupIdentifier(randomAlphabetic(10))
        .build();
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testCreate() {
    String accountId = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).orgIdentifier(orgIdentifier).build();
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment roleAssignment = getRoleAssignment(scope);
    RoleAssignment roleAssignmentClone = (RoleAssignment) HObjectMapper.clone(roleAssignment);
    ValidationResult validResult = ValidationResult.VALID;
    ArgumentCaptor<RoleAssignmentValidationRequest> roleAssignmentValidationRequestArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentValidationRequest.class);

    RoleAssignmentValidationResult roleAssignmentValidationResult = RoleAssignmentValidationResult.builder()
                                                                        .roleValidationResult(validResult)
                                                                        .principalValidationResult(validResult)
                                                                        .scopeValidationResult(validResult)
                                                                        .resourceGroupValidationResult(validResult)
                                                                        .build();
    when(outboxTransactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(outboxService.save(any())).thenReturn(null);
    ArgumentCaptor<RoleAssignmentCreateEvent> argumentCaptor = ArgumentCaptor.forClass(RoleAssignmentCreateEvent.class);
    when(roleAssignmentDao.create(any())).thenReturn(roleAssignment);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(roleAssignmentValidator.validate(any())).thenReturn(roleAssignmentValidationResult);
    RoleAssignment result = roleAssignmentService.create(roleAssignment);
    verify(roleAssignmentValidator, times(1)).validate(roleAssignmentValidationRequestArgumentCaptor.capture());
    verify(outboxService, times(1)).save(argumentCaptor.capture());
    RoleAssignmentCreateEvent roleAssignmentCreateEvent = argumentCaptor.getValue();
    assertEquals(scopeDTO, roleAssignmentCreateEvent.getScope());
    assertEquals(scopeDTO.getAccountIdentifier(), roleAssignmentCreateEvent.getAccountIdentifier());
    assertEquals(RoleAssignmentDTOMapper.toDTO(roleAssignment), roleAssignmentCreateEvent.getRoleAssignment());
    assertEquals(roleAssignmentClone, result);
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testCreateInvalidParameters() throws IllegalAccessException {
    String accountId = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).orgIdentifier(orgIdentifier).build();
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment roleAssignment = getRoleAssignment(scope);
    ValidationResult validResult = ValidationResult.VALID;
    ValidationResult invalidResult = ValidationResult.builder().valid(false).errorMessage(randomAlphabetic(10)).build();

    RoleAssignmentValidationResult roleAssignmentValidationResult = RoleAssignmentValidationResult.builder()
                                                                        .roleValidationResult(validResult)
                                                                        .principalValidationResult(validResult)
                                                                        .scopeValidationResult(validResult)
                                                                        .resourceGroupValidationResult(validResult)
                                                                        .build();
    when(roleAssignmentValidator.validate(any())).thenReturn(roleAssignmentValidationResult);

    List<Field> validationFields = new ArrayList<>();
    validationFields.add(ReflectionUtils.getFieldByName(RoleAssignmentValidationResult.class, "roleValidationResult"));
    validationFields.add(
        ReflectionUtils.getFieldByName(RoleAssignmentValidationResult.class, "principalValidationResult"));
    validationFields.add(ReflectionUtils.getFieldByName(RoleAssignmentValidationResult.class, "scopeValidationResult"));
    validationFields.add(
        ReflectionUtils.getFieldByName(RoleAssignmentValidationResult.class, "resourceGroupValidationResult"));

    int idx = 0;
    for (Field field : validationFields) {
      idx++;
      ReflectionUtils.setObjectField(field, roleAssignmentValidationResult, invalidResult);
      try {
        roleAssignmentService.create(roleAssignment);
        fail();
      } catch (InvalidRequestException exception) {
        verify(roleAssignmentValidator, times(idx)).validate(any());
        ReflectionUtils.setObjectField(field, roleAssignmentValidationResult, validResult);
        verify(roleAssignmentDao, never()).create(any());
      }
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    String accountId = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).orgIdentifier(orgIdentifier).build();
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment currentRoleAssignment = RoleAssignment.builder()
                                               .identifier(randomAlphabetic(10))
                                               .scopeIdentifier(scope.toString())
                                               .roleIdentifier(randomAlphabetic(10))
                                               .principalType(PrincipalType.USER)
                                               .principalIdentifier(randomAlphabetic(10))
                                               .principalScopeLevel(randomAlphabetic(10))
                                               .resourceGroupIdentifier(randomAlphabetic(10))
                                               .disabled(true)
                                               .version(17L)
                                               .build();
    RoleAssignment roleAssignmentUpdate =
        RoleAssignment.builder()
            .identifier(currentRoleAssignment.getIdentifier())
            .scopeIdentifier(currentRoleAssignment.getScopeIdentifier())
            .roleIdentifier(currentRoleAssignment.getRoleIdentifier())
            .principalType(currentRoleAssignment.getPrincipalType())
            .principalIdentifier(currentRoleAssignment.getPrincipalIdentifier())
            .principalScopeLevel(currentRoleAssignment.getPrincipalScopeLevel())
            .resourceGroupIdentifier(currentRoleAssignment.getResourceGroupIdentifier())
            .disabled(false)
            .build();
    RoleAssignment updatedRoleAssignment = (RoleAssignment) HObjectMapper.clone(roleAssignmentUpdate);
    updatedRoleAssignment.setVersion(currentRoleAssignment.getVersion() + 1);
    RoleAssignment updatedRoleAssignmentClone = (RoleAssignment) HObjectMapper.clone(updatedRoleAssignment);

    when(outboxTransactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(outboxService.save(any())).thenReturn(null);
    ArgumentCaptor<RoleAssignmentUpdateEvent> argumentCaptor = ArgumentCaptor.forClass(RoleAssignmentUpdateEvent.class);
    when(roleAssignmentDao.update(any())).thenReturn(updatedRoleAssignment);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    when(roleAssignmentDao.get(roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier()))
        .thenReturn(Optional.of(currentRoleAssignment));
    RoleAssignment updatedRoleAssignment1 = roleAssignmentService.update(roleAssignmentUpdate);
    assertEquals(updatedRoleAssignmentClone, updatedRoleAssignment1);
    verify(outboxTransactionTemplate, times(1)).execute(any());

    verify(outboxService, times(1)).save(argumentCaptor.capture());
    RoleAssignmentUpdateEvent roleAssignmentUpdateEvent = argumentCaptor.getValue();
    assertEquals(scopeDTO, roleAssignmentUpdateEvent.getScope());
    assertEquals(scopeDTO.getAccountIdentifier(), roleAssignmentUpdateEvent.getAccountIdentifier());
    assertEquals(
        RoleAssignmentDTOMapper.toDTO(updatedRoleAssignment), roleAssignmentUpdateEvent.getNewRoleAssignment());
    assertEquals(
        RoleAssignmentDTOMapper.toDTO(currentRoleAssignment), roleAssignmentUpdateEvent.getOldRoleAssignment());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    String accountId = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).orgIdentifier(orgIdentifier).build();
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment roleAssignmentUpdate = getRoleAssignment(scope);
    when(roleAssignmentDao.get(roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    roleAssignmentService.update(roleAssignmentUpdate);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateInvalidUpdate() throws IllegalAccessException {
    String accountId = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).orgIdentifier(orgIdentifier).build();
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment currentRoleAssignment = getRoleAssignment(scope);
    RoleAssignment currentRoleAssignmentClone = (RoleAssignment) HObjectMapper.clone(currentRoleAssignment);
    RoleAssignment roleAssignmentUpdate =
        RoleAssignment.builder()
            .identifier(currentRoleAssignment.getIdentifier())
            .scopeIdentifier(currentRoleAssignment.getScopeIdentifier())
            .roleIdentifier(currentRoleAssignment.getRoleIdentifier())
            .principalType(currentRoleAssignment.getPrincipalType())
            .principalIdentifier(currentRoleAssignment.getPrincipalIdentifier())
            .principalScopeLevel(currentRoleAssignment.getPrincipalScopeLevel())
            .resourceGroupIdentifier(currentRoleAssignment.getResourceGroupIdentifier())
            .build();
    when(roleAssignmentDao.get(roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier()))
        .thenReturn(Optional.of(currentRoleAssignment));
    List<Pair<Field, Object> > fieldsAndNewValues = new ArrayList<>();
    fieldsAndNewValues.add(
        Pair.of(ReflectionUtils.getFieldByName(RoleAssignment.class, "resourceGroupIdentifier"), randomAlphabetic(11)));
    fieldsAndNewValues.add(
        Pair.of(ReflectionUtils.getFieldByName(RoleAssignment.class, "roleIdentifier"), randomAlphabetic(11)));
    fieldsAndNewValues.add(
        Pair.of(ReflectionUtils.getFieldByName(RoleAssignment.class, "principalIdentifier"), randomAlphabetic(11)));
    fieldsAndNewValues.add(
        Pair.of(ReflectionUtils.getFieldByName(RoleAssignment.class, "principalScopeLevel"), randomAlphabetic(11)));
    fieldsAndNewValues.add(
        Pair.of(ReflectionUtils.getFieldByName(RoleAssignment.class, "principalType"), PrincipalType.SERVICE_ACCOUNT));

    int idx = 0;
    for (Pair<Field, Object> fieldAndValue : fieldsAndNewValues) {
      idx++;
      ReflectionUtils.setObjectField(fieldAndValue.getLeft(), roleAssignmentUpdate, fieldAndValue.getRight());
      try {
        roleAssignmentService.update(roleAssignmentUpdate);
        fail();
      } catch (InvalidRequestException exception) {
        verify(roleAssignmentDao, times(idx)).get(any(), any());
        ReflectionUtils.setObjectField(fieldAndValue.getLeft(), roleAssignmentUpdate,
            ReflectionUtils.getFieldValue(currentRoleAssignmentClone, fieldAndValue.getLeft()));
        verify(roleAssignmentDao, never()).update(any());
      }
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String roleIdentifier = randomAlphabetic(10);
    String accountId = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).build();
    Scope scope = fromDTO(scopeDTO);
    String scopeIdentifier = scope.toString();
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(outboxService.save(any())).thenReturn(null);
    when(outboxTransactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(roleAssignmentDao.delete(identifier, scopeIdentifier))
        .thenReturn(Optional.of(RoleAssignment.builder()
                                    .scopeIdentifier(scopeIdentifier)
                                    .identifier(identifier)
                                    .roleIdentifier(roleIdentifier)
                                    .build()));
    Optional<RoleAssignment> deletedRoleAssignment = roleAssignmentService.delete(identifier, scopeIdentifier);
    assertTrue(deletedRoleAssignment.isPresent());
    assertEquals(identifier, deletedRoleAssignment.get().getIdentifier());
    assertEquals(scopeIdentifier, deletedRoleAssignment.get().getScopeIdentifier());
    assertEquals(roleIdentifier, deletedRoleAssignment.get().getRoleIdentifier());
    verify(roleAssignmentDao, times(1)).delete(identifier, scopeIdentifier);
    ArgumentCaptor<RoleAssignmentDeleteEvent> deleteEventArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentDeleteEvent.class);
    verify(outboxService, times(1)).save(deleteEventArgumentCaptor.capture());
    RoleAssignmentDeleteEvent roleAssignmentDeleteEvent = deleteEventArgumentCaptor.getValue();
    assertFalse(roleAssignmentDeleteEvent.getSkipAudit());
    assertEquals(scopeDTO, roleAssignmentDeleteEvent.getScope());
    RoleAssignmentDTO roleAssignmentDTO = RoleAssignmentDTOMapper.toDTO(deletedRoleAssignment.get());
    assertEquals(roleAssignmentDTO, roleAssignmentDeleteEvent.getRoleAssignment());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    String accountId = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).build();
    Scope scope = fromDTO(scopeDTO);
    RoleAssignmentFilter roleAssignmentFilter =
        RoleAssignmentFilter.builder().scopeFilter(randomAlphabetic(10)).build();
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(outboxService.save(any())).thenReturn(null);
    when(outboxTransactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    List<RoleAssignment> roleAssingmentsDeleted =
        List.of(RoleAssignment.builder().build(), RoleAssignment.builder().build(), RoleAssignment.builder().build());
    when(roleAssignmentDao.findAndRemove(roleAssignmentFilter)).thenReturn(roleAssingmentsDeleted);
    long deletedRoleAssignmentsCount = roleAssignmentService.deleteMulti(roleAssignmentFilter);
    ArgumentCaptor<RoleAssignmentDeleteEvent> deleteEventArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentDeleteEvent.class);
    verify(outboxService, times(roleAssingmentsDeleted.size())).save(deleteEventArgumentCaptor.capture());
    RoleAssignmentDeleteEvent roleAssignmentDeleteEvent = deleteEventArgumentCaptor.getValue();
    assertTrue(roleAssignmentDeleteEvent.getSkipAudit());
    assertEquals(3L, deletedRoleAssignmentsCount);
    verify(roleAssignmentDao, times(1)).findAndRemove(roleAssignmentFilter);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMultiWithScopeIdentifierAndIdentifier() {
    String accountId = randomAlphabetic(10);
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(accountId).build();
    Scope scope = fromDTO(scopeDTO);
    List<String> identifiers = List.of("identifier1", "identifier2", "identifier3");
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(outboxService.save(any())).thenReturn(null);
    when(outboxTransactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    List<RoleAssignment> roleAssingmentsDeleted =
        List.of(RoleAssignment.builder().build(), RoleAssignment.builder().build(), RoleAssignment.builder().build());
    when(roleAssignmentDao.findAndRemove(scope.toString(), identifiers)).thenReturn(roleAssingmentsDeleted);
    List<RoleAssignment> roleAssignments = roleAssignmentService.deleteMulti(scope.toString(), identifiers);
    ArgumentCaptor<RoleAssignmentDeleteEvent> deleteEventArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentDeleteEvent.class);
    verify(outboxService, times(roleAssingmentsDeleted.size())).save(deleteEventArgumentCaptor.capture());
    RoleAssignmentDeleteEvent roleAssignmentDeleteEvent = deleteEventArgumentCaptor.getValue();
    assertFalse(roleAssignmentDeleteEvent.getSkipAudit());
    assertEquals(3L, roleAssignments.size());
    verify(roleAssignmentDao, times(1)).findAndRemove(scope.toString(), identifiers);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidate() {
    RoleAssignmentValidationRequest roleAssignmentValidationRequest =
        RoleAssignmentValidationRequest.builder().validateRole(true).build();
    when(roleAssignmentValidator.validate(roleAssignmentValidationRequest))
        .thenReturn(RoleAssignmentValidationResult.builder().roleValidationResult(ValidationResult.VALID).build());
    RoleAssignmentValidationResult validationResult = roleAssignmentService.validate(roleAssignmentValidationRequest);
    assertNotNull(validationResult);
    assertEquals(ValidationResult.VALID, validationResult.getRoleValidationResult());
    verify(roleAssignmentValidator, times(1)).validate(roleAssignmentValidationRequest);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    String roleIdentifier = randomAlphabetic(10);
    when(roleAssignmentDao.get(identifier, scopeIdentifier))
        .thenReturn(Optional.of(RoleAssignment.builder()
                                    .scopeIdentifier(scopeIdentifier)
                                    .identifier(identifier)
                                    .roleIdentifier(roleIdentifier)
                                    .build()));
    Optional<RoleAssignment> roleAssignment = roleAssignmentService.get(identifier, scopeIdentifier);
    assertTrue(roleAssignment.isPresent());
    assertEquals(identifier, roleAssignment.get().getIdentifier());
    assertEquals(scopeIdentifier, roleAssignment.get().getScopeIdentifier());
    assertEquals(roleIdentifier, roleAssignment.get().getRoleIdentifier());
    verify(roleAssignmentDao, times(1)).get(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).build();
    RoleAssignmentFilter roleAssignmentFilter =
        RoleAssignmentFilter.builder().scopeFilter(randomAlphabetic(10)).build();
    when(roleAssignmentDao.list(pageRequest, roleAssignmentFilter, true))
        .thenReturn(PageResponse.getEmptyPageResponse(pageRequest));
    PageResponse<RoleAssignment> pageResponse = roleAssignmentService.list(pageRequest, roleAssignmentFilter);
    assertTrue(pageResponse.isEmpty());
    verify(roleAssignmentDao, times(1)).list(pageRequest, roleAssignmentFilter, true);
  }
}
