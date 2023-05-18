/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.validator;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.PrincipalValidator;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class RoleAssignmentValidatorTest extends AccessControlCoreTestBase {
  private PrincipalValidator principalValidator;
  private Map<PrincipalType, PrincipalValidator> principalValidatorByType;
  private RoleService roleService;
  private ResourceGroupService resourceGroupService;
  private ScopeService scopeService;
  private RoleAssignmentValidatorImpl roleAssignmentValidator;
  private static final ValidationResult validResult = ValidationResult.VALID;
  private static final ValidationResult invalidResult =
      ValidationResult.builder().valid(false).errorMessage("").build();
  private static final Set<PrincipalType> validPrincipalTypesForValidator =
      Sets.newHashSet(PrincipalType.USER, PrincipalType.USER_GROUP, PrincipalType.SERVICE_ACCOUNT);

  @Before
  public void setup() {
    principalValidator = mock(PrincipalValidator.class);
    principalValidatorByType = new HashMap<>();
    principalValidatorByType.put(PrincipalType.USER, principalValidator);
    principalValidatorByType.put(PrincipalType.SERVICE_ACCOUNT, principalValidator);
    principalValidatorByType.put(PrincipalType.USER_GROUP, principalValidator);
    roleService = mock(RoleService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    scopeService = mock(ScopeService.class);
    roleAssignmentValidator =
        spy(new RoleAssignmentValidatorImpl(principalValidatorByType, roleService, resourceGroupService, scopeService));
  }

  private RoleAssignment getRoleAssignment(PrincipalType principalType) {
    return RoleAssignment.builder()
        .identifier(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .roleIdentifier(randomAlphabetic(10))
        .principalType(principalType)
        .principalIdentifier(randomAlphabetic(10))
        .resourceGroupIdentifier(randomAlphabetic(10))
        .build();
  }

  private RoleAssignment getRoleAssignment() {
    return getRoleAssignment(PrincipalType.USER);
  }

  private void assertRoleAssignmentValidationResult(RoleAssignmentValidationResult validationResult,
      ValidationResult scopeValidationResult, ValidationResult principalValidationResult,
      ValidationResult roleValidationResult, ValidationResult resourceGroupValidationResult) {
    assertEquals(scopeValidationResult.isValid(), validationResult.getScopeValidationResult().isValid());
    assertEquals(principalValidationResult.isValid(), validationResult.getPrincipalValidationResult().isValid());
    assertEquals(roleValidationResult.isValid(), validationResult.getRoleValidationResult().isValid());
    assertEquals(
        resourceGroupValidationResult.isValid(), validationResult.getResourceGroupValidationResult().isValid());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidateScope() {
    RoleAssignment roleAssignment = getRoleAssignment();

    RoleAssignmentValidationRequest roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                                                          .roleAssignment(roleAssignment)
                                                                          .validateScope(false)
                                                                          .validatePrincipal(false)
                                                                          .validateRole(false)
                                                                          .validateResourceGroup(false)
                                                                          .build();
    RoleAssignmentValidationResult validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(scopeService, times(0)).isPresent(roleAssignment.getScopeIdentifier());
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                          .roleAssignment(roleAssignment)
                                          .validateScope(true)
                                          .validatePrincipal(false)
                                          .validateRole(false)
                                          .validateResourceGroup(false)
                                          .build();
    when(scopeService.isPresent(roleAssignment.getScopeIdentifier())).thenReturn(true);
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(scopeService, times(1)).isPresent(roleAssignment.getScopeIdentifier());
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    when(scopeService.isPresent(roleAssignment.getScopeIdentifier())).thenReturn(false);
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(scopeService, times(2)).isPresent(roleAssignment.getScopeIdentifier());
    assertRoleAssignmentValidationResult(validationResult, invalidResult, validResult, validResult, validResult);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidatePrincipal() {
    RoleAssignment roleAssignment = getRoleAssignment();
    Principal principal = Principal.builder()
                              .principalIdentifier(roleAssignment.getPrincipalIdentifier())
                              .principalType(roleAssignment.getPrincipalType())
                              .build();

    RoleAssignmentValidationRequest roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                                                          .roleAssignment(roleAssignment)
                                                                          .validateScope(false)
                                                                          .validatePrincipal(false)
                                                                          .validateRole(false)
                                                                          .validateResourceGroup(false)
                                                                          .build();
    RoleAssignmentValidationResult validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(principalValidator, times(0)).validatePrincipal(principal, roleAssignment.getScopeIdentifier());
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                          .roleAssignment(roleAssignment)
                                          .validateScope(false)
                                          .validatePrincipal(true)
                                          .validateRole(false)
                                          .validateResourceGroup(false)
                                          .build();
    when(principalValidator.validatePrincipal(principal, roleAssignment.getScopeIdentifier())).thenReturn(validResult);
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(principalValidator, times(1)).validatePrincipal(principal, roleAssignment.getScopeIdentifier());
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    when(principalValidator.validatePrincipal(principal, roleAssignment.getScopeIdentifier()))
        .thenReturn(invalidResult);
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(principalValidator, times(2)).validatePrincipal(principal, roleAssignment.getScopeIdentifier());
    assertRoleAssignmentValidationResult(validationResult, validResult, invalidResult, validResult, validResult);

    Set<PrincipalType> allPrincipalTypes = new HashSet<>(Arrays.asList(PrincipalType.values()));
    Set<PrincipalType> invalidPrincipalTypesForValidator =
        Sets.difference(allPrincipalTypes, validPrincipalTypesForValidator);
    for (PrincipalType principalType : invalidPrincipalTypesForValidator) {
      roleAssignment = getRoleAssignment(principalType);
      roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                            .roleAssignment(roleAssignment)
                                            .validateScope(false)
                                            .validatePrincipal(true)
                                            .validateRole(false)
                                            .validateResourceGroup(false)
                                            .build();
      validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
      verify(principalValidator, times(2)).validatePrincipal(any(), any());
      assertRoleAssignmentValidationResult(validationResult, validResult, invalidResult, validResult, validResult);
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidateRole() {
    RoleAssignment roleAssignment = getRoleAssignment();

    RoleAssignmentValidationRequest roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                                                          .roleAssignment(roleAssignment)
                                                                          .validateScope(false)
                                                                          .validatePrincipal(false)
                                                                          .validateRole(false)
                                                                          .validateResourceGroup(false)
                                                                          .build();
    RoleAssignmentValidationResult validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(roleService, times(0))
        .get(roleAssignment.getRoleIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                          .roleAssignment(roleAssignment)
                                          .validateScope(false)
                                          .validatePrincipal(false)
                                          .validateRole(true)
                                          .validateResourceGroup(false)
                                          .build();
    when(roleService.get(
             roleAssignment.getRoleIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(Role.builder()
                                    .scopeIdentifier(roleAssignment.getScopeIdentifier())
                                    .identifier(roleAssignment.getRoleIdentifier())
                                    .build()));
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(roleService, times(1))
        .get(roleAssignment.getRoleIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    when(roleService.get(
             roleAssignment.getRoleIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.empty());
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(roleService, times(2))
        .get(roleAssignment.getRoleIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, invalidResult, validResult);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidateResourceGroup() {
    RoleAssignment roleAssignment = getRoleAssignment();

    RoleAssignmentValidationRequest roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                                                          .roleAssignment(roleAssignment)
                                                                          .validateScope(false)
                                                                          .validatePrincipal(false)
                                                                          .validateRole(false)
                                                                          .validateResourceGroup(false)
                                                                          .build();
    RoleAssignmentValidationResult validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(resourceGroupService, times(0))
        .get(roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    roleAssignmentValidationRequest = RoleAssignmentValidationRequest.builder()
                                          .roleAssignment(roleAssignment)
                                          .validateScope(false)
                                          .validatePrincipal(false)
                                          .validateRole(false)
                                          .validateResourceGroup(true)
                                          .build();
    when(resourceGroupService.get(
             roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.of(ResourceGroup.builder()
                                    .scopeIdentifier(roleAssignment.getScopeIdentifier())
                                    .identifier(roleAssignment.getRoleIdentifier())
                                    .build()));
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(resourceGroupService, times(1))
        .get(roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, validResult);

    when(resourceGroupService.get(
             roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER))
        .thenReturn(Optional.empty());
    validationResult = roleAssignmentValidator.validate(roleAssignmentValidationRequest);
    verify(resourceGroupService, times(2))
        .get(roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    assertRoleAssignmentValidationResult(validationResult, validResult, validResult, validResult, invalidResult);
  }
}
