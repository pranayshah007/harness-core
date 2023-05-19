/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDao;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationRequest;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationResult;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidator;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
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

@OwnedBy(PL)
public class RoleAssignmentServiceImplTest extends AccessControlCoreTestBase {
  private RoleAssignmentDao roleAssignmentDao;
  private RoleAssignmentValidator roleAssignmentValidator;
  private RoleAssignmentServiceImpl roleAssignmentService;

  @Before
  public void setup() {
    roleAssignmentDao = mock(RoleAssignmentDao.class);
    roleAssignmentValidator = mock(RoleAssignmentValidator.class);
    roleAssignmentService = spy(new RoleAssignmentServiceImpl(roleAssignmentDao, roleAssignmentValidator));
  }

  private RoleAssignment getRoleAssignment() {
    return RoleAssignment.builder()
        .identifier(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .roleIdentifier(randomAlphabetic(10))
        .principalType(PrincipalType.USER)
        .principalIdentifier(randomAlphabetic(10))
        .principalScopeLevel(randomAlphabetic(10))
        .resourceGroupIdentifier(randomAlphabetic(10))
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    RoleAssignment roleAssignment = getRoleAssignment();
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
    when(roleAssignmentValidator.validate(any())).thenReturn(roleAssignmentValidationResult);
    when(roleAssignmentDao.create(roleAssignmentClone)).thenReturn(roleAssignmentClone);

    RoleAssignment result = roleAssignmentService.create(roleAssignment);
    verify(roleAssignmentValidator, times(1)).validate(roleAssignmentValidationRequestArgumentCaptor.capture());
    RoleAssignmentValidationRequest roleAssignmentValidationRequest =
        roleAssignmentValidationRequestArgumentCaptor.getValue();

    assertTrue(roleAssignmentValidationRequest.isValidatePrincipal());
    assertTrue(roleAssignmentValidationRequest.isValidateResourceGroup());
    assertTrue(roleAssignmentValidationRequest.isValidateRole());
    assertTrue(roleAssignmentValidationRequest.isValidateScope());
    assertEquals(roleAssignmentClone, roleAssignmentValidationRequest.getRoleAssignment());
    assertEquals(result, roleAssignmentClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateInvalidParameters() throws IllegalAccessException {
    RoleAssignment roleAssignment = getRoleAssignment();
    ValidationResult validResult = ValidationResult.VALID;
    ValidationResult invalidResult = ValidationResult.builder().valid(false).errorMessage(randomAlphabetic(10)).build();

    RoleAssignmentValidationResult roleAssignmentValidationResult = RoleAssignmentValidationResult.builder()
                                                                        .roleValidationResult(validResult)
                                                                        .principalValidationResult(validResult)
                                                                        .scopeValidationResult(validResult)
                                                                        .resourceGroupValidationResult(validResult)
                                                                        .build();
    when(roleAssignmentValidator.validate(any())).thenReturn(roleAssignmentValidationResult);
    when(roleAssignmentDao.create(any())).thenReturn(any());

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
      }
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    RoleAssignment currentRoleAssignment = RoleAssignment.builder()
                                               .identifier(randomAlphabetic(10))
                                               .scopeIdentifier(randomAlphabetic(10))
                                               .roleIdentifier(randomAlphabetic(10))
                                               .principalType(PrincipalType.USER)
                                               .principalIdentifier(randomAlphabetic(10))
                                               .principalScopeLevel(randomAlphabetic(10))
                                               .resourceGroupIdentifier(randomAlphabetic(10))
                                               .disabled(true)
                                               .version(17L)
                                               .build();
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
            .disabled(false)
            .build();
    RoleAssignment updatedRoleAssignment = (RoleAssignment) HObjectMapper.clone(roleAssignmentUpdate);
    updatedRoleAssignment.setVersion(currentRoleAssignment.getVersion() + 1);
    RoleAssignment updatedRoleAssignmentClone = (RoleAssignment) HObjectMapper.clone(updatedRoleAssignment);

    when(roleAssignmentDao.get(roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier()))
        .thenReturn(Optional.of(currentRoleAssignment));
    ArgumentCaptor<RoleAssignment> roleAssignmentArgumentCaptor = ArgumentCaptor.forClass(RoleAssignment.class);
    when(roleAssignmentDao.update(any())).thenReturn(updatedRoleAssignment);

    RoleAssignmentUpdateResult roleAssignmentUpdateResult = roleAssignmentService.update(roleAssignmentUpdate);

    assertEquals(currentRoleAssignmentClone, roleAssignmentUpdateResult.getOriginalRoleAssignment());
    assertEquals(updatedRoleAssignmentClone, roleAssignmentUpdateResult.getUpdatedRoleAssignment());

    verify(roleAssignmentDao, times(1)).update(roleAssignmentArgumentCaptor.capture());
    RoleAssignment requestedUpdate = roleAssignmentArgumentCaptor.getValue();
    assertEquals(updatedRoleAssignmentClone.getScopeIdentifier(), requestedUpdate.getScopeIdentifier());
    assertEquals(updatedRoleAssignmentClone.getIdentifier(), requestedUpdate.getIdentifier());
    assertEquals(updatedRoleAssignmentClone.getRoleIdentifier(), requestedUpdate.getRoleIdentifier());
    assertEquals(updatedRoleAssignmentClone.getPrincipalIdentifier(), requestedUpdate.getPrincipalIdentifier());
    assertEquals(updatedRoleAssignmentClone.getPrincipalType(), requestedUpdate.getPrincipalType());
    assertEquals(updatedRoleAssignmentClone.getResourceGroupIdentifier(), requestedUpdate.getResourceGroupIdentifier());
    assertEquals(17L, requestedUpdate.getVersion().longValue());
    assertFalse(requestedUpdate.isDisabled());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    RoleAssignment roleAssignmentUpdate = getRoleAssignment();
    when(roleAssignmentDao.get(roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    roleAssignmentService.update(roleAssignmentUpdate);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateInvalidUpdate() throws IllegalAccessException {
    RoleAssignment currentRoleAssignment = getRoleAssignment();
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
      }
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    String roleIdentifier = randomAlphabetic(10);
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
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    RoleAssignmentFilter roleAssignmentFilter =
        RoleAssignmentFilter.builder().scopeFilter(randomAlphabetic(10)).build();
    when(roleAssignmentDao.deleteMulti(roleAssignmentFilter)).thenReturn(17L);
    long deletedRoleAssignmentsCount = roleAssignmentService.deleteMulti(roleAssignmentFilter);
    assertEquals(17L, deletedRoleAssignmentsCount);
    verify(roleAssignmentDao, times(1)).deleteMulti(roleAssignmentFilter);
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
