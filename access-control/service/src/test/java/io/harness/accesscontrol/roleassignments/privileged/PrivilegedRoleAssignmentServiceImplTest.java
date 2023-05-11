/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.privileged;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.NAMANG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.accesscontrol.acl.PermissionCheckResult;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDao;
import io.harness.accesscontrol.roles.PrivilegedRole;
import io.harness.accesscontrol.support.SupportPreference;
import io.harness.accesscontrol.support.SupportService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class PrivilegedRoleAssignmentServiceImplTest extends AccessControlTestBase {
  private PrivilegedRoleAssignmentDao privilegedRoleAssignmentDao;
  private SupportService supportService;
  private PrivilegedRoleAssignmentServiceImpl privilegedRoleAssignmentService;
  private PrivilegedRoleAssignmentServiceImpl origPrivilegedRoleAssignmentService;
  private static final int NumberOfRoles = 5; // no of privileged roles to built
  private static final String PRIVILEGED_ROLES_FIELD = "privilegedRoles";
  private static final String DEMO_ROLE_IDENTIFIER = "DEMO_IDENTIFIER";
  private Field privilegedRolesField;
  private List<Principal> testPrincipalList;

  @Before
  public void setup() throws NoSuchFieldException, IllegalAccessException {
    privilegedRoleAssignmentDao = mock(PrivilegedRoleAssignmentDao.class);
    supportService = mock(SupportService.class);
    origPrivilegedRoleAssignmentService =
        new PrivilegedRoleAssignmentServiceImpl(privilegedRoleAssignmentDao, supportService);

    // overriding set of privileged roles
    privilegedRolesField = origPrivilegedRoleAssignmentService.getClass().getDeclaredField(PRIVILEGED_ROLES_FIELD);

    // Set the accessibility as true
    privilegedRolesField.setAccessible(true);
    ReflectionUtils.setObjectField(privilegedRolesField, origPrivilegedRoleAssignmentService, buildPrivilegedRoles());
    privilegedRolesField.setAccessible(false);

    privilegedRoleAssignmentService = spy(origPrivilegedRoleAssignmentService);

    testPrincipalList = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      testPrincipalList.add(Principal.builder().principalType(PrincipalType.USER).principalIdentifier("P" + i).build());
    }
  }

  private PrivilegedRole buildPrivilegedRole(int permissionCount) {
    Set<String> permissions = new HashSet<>();
    for (int i = 0; i < permissionCount; i++) {
      permissions.add(randomAlphabetic(10));
    }
    return PrivilegedRole.builder()
        .identifier(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .permissions(permissions)
        .build();
  }

  private Set<PrivilegedRole> buildPrivilegedRoles() {
    // linked hash set because to fox the iteration order
    Set<PrivilegedRole> privilegedRolesCreated = new LinkedHashSet<>();
    for (int i = 0; i < NumberOfRoles; i++) {
      privilegedRolesCreated.add(buildPrivilegedRole(ThreadLocalRandom.current().nextInt(1, 101)));
    }
    return privilegedRolesCreated;
  }

  private SupportPreference buildSupportPreference(boolean isSupportEnabled) {
    return SupportPreference.builder()
        .accountIdentifier(randomAlphabetic(10))
        .isSupportEnabled(isSupportEnabled)
        .build();
  }

  private PrivilegedAccessCheck buildPrivilegedAccessCheck(int noOfTopRolesInPrivilegedAccessCheck) {
    assertTrue("NumberOfRoles (" + NumberOfRoles
            + ") should be greater than equal to noOfTopRolesInPrivilegedAccessCheck ("
            + noOfTopRolesInPrivilegedAccessCheck + ")",
        NumberOfRoles >= noOfTopRolesInPrivilegedAccessCheck);
    privilegedRolesField.setAccessible(true);
    Object objectToCheckPermissionFor =
        ReflectionUtils.getFieldValue(origPrivilegedRoleAssignmentService, PRIVILEGED_ROLES_FIELD);
    privilegedRolesField.setAccessible(false);

    assertTrue(objectToCheckPermissionFor instanceof LinkedHashSet<?>);

    LinkedHashSet<?> privilegedRoles = (LinkedHashSet<?>) objectToCheckPermissionFor;
    List<PermissionCheck> permissionChecks =
        privilegedRoles.stream()
            .filter(privilegedRole -> privilegedRole instanceof PrivilegedRole)
            .map(privilegedRole -> (PrivilegedRole) privilegedRole)
            .limit(noOfTopRolesInPrivilegedAccessCheck)
            .map(PrivilegedRole::getPermissions)
            .map(permissionSet -> new ArrayList<String>())
            .flatMap(ArrayList::stream)
            .map(permission
                -> PermissionCheck.builder().resourceType(randomAlphabetic(10)).permission(permission).build())
            .collect(Collectors.toList());
    // necessary to maintain order of permissions  :  .flatMap(ArrayList::stream)

    return PrivilegedAccessCheck.builder()
        .accountIdentifier(randomAlphabetic(10))
        .principal(
            Principal.builder().principalType(PrincipalType.USER).principalIdentifier(randomAlphabetic(5)).build())
        .permissionChecks(permissionChecks)
        .build();
  }

  private List<PrivilegedRoleAssignment> buildPrivilegedRoleAssignments(
      int noOfBottomRolesInPrivilegedRoleAssignments) {
    assertTrue("NumberOfRoles (" + NumberOfRoles
            + ") should be greater than equal to noOfTopRolesInPrivilegedAccessCheck ("
            + noOfBottomRolesInPrivilegedRoleAssignments + ")",
        NumberOfRoles >= noOfBottomRolesInPrivilegedRoleAssignments);

    privilegedRolesField.setAccessible(true);
    Object objectToCheckPermissionFor =
        ReflectionUtils.getFieldValue(origPrivilegedRoleAssignmentService, PRIVILEGED_ROLES_FIELD);
    privilegedRolesField.setAccessible(false);

    assertTrue(objectToCheckPermissionFor instanceof LinkedHashSet<?>);

    LinkedHashSet<?> privilegedRoles = (LinkedHashSet<?>) objectToCheckPermissionFor;

    return privilegedRoles.stream()
        .filter(privilegedRole -> privilegedRole instanceof PrivilegedRole)
        .map(privilegedRole -> (PrivilegedRole) privilegedRole)
        .skip((long) NumberOfRoles - noOfBottomRolesInPrivilegedRoleAssignments)
        .map(PrivilegedRole::getIdentifier)
        .map(roleIdentifier
            -> PrivilegedRoleAssignment.builder()
                   .principalType(PrincipalType.USER)
                   .principalIdentifier(randomAlphabetic(10))
                   .roleIdentifier(roleIdentifier)
                   .build())
        .collect(Collectors.toList());
  }

  private long noOfPermissionsInTopNRoles(int noOfTopRoles) {
    assertTrue(
        "NumberOfRoles (" + NumberOfRoles + ") should be greater than equal to noOfTopRoles (" + noOfTopRoles + ")",
        NumberOfRoles >= noOfTopRoles);

    privilegedRolesField.setAccessible(true);
    Object objectToCheckPermissionFor =
        ReflectionUtils.getFieldValue(origPrivilegedRoleAssignmentService, PRIVILEGED_ROLES_FIELD);
    privilegedRolesField.setAccessible(false);

    assertTrue(objectToCheckPermissionFor instanceof LinkedHashSet<?>);

    LinkedHashSet<?> privilegedRoles = (LinkedHashSet<?>) objectToCheckPermissionFor;
    return privilegedRoles.stream()
        .filter(privilegedRole -> privilegedRole instanceof PrivilegedRole)
        .map(privilegedRole -> (PrivilegedRole) privilegedRole)
        .limit(noOfTopRoles)
        .map(PrivilegedRole::getPermissions)
        .flatMap(Set::stream)
        .count();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceEnabledAndRoleNotExisting() {
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(true));

    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(0));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertFalse(permissionCheckResults.get(i).isPermitted());
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceEnabledAndRoleExistingAndNotMatching() {
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(true));
    assertTrue("For this testcase, number of roles should be greater than 1", NumberOfRoles > 1);
    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles - 1);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(1));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertFalse(permissionCheckResults.get(i).isPermitted());
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceEnabledAndRoleExistingAndMatching() {
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(true));

    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(NumberOfRoles));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertTrue(permissionCheckResults.get(i).isPermitted());
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceEnabledAndRoleExistingAndSomeMatching() {
    // so that in check, permission corresponding to NumberOfRoles-3 won't match
    // and permission corresponding to last two roles will only match
    int noOfPermissions = (int) noOfPermissionsInTopNRoles(NumberOfRoles - 3);
    assertTrue("For this testcase, number of roles should be greater than 3", NumberOfRoles > 3);
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(true));

    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles - 1);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(3));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    boolean condition = false;
    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertEquals(permissionCheckResults.get(i).isPermitted(), condition);
      if (i == (noOfPermissions - 1)) {
        condition = true;
      }
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceNotEnabledAndRoleNotExisting() {
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(false));

    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(0));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertFalse(permissionCheckResults.get(i).isPermitted());
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceNotEnabledAndRoleExistingAndNotMatching() {
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(false));
    assertTrue("For this testcase, number of roles should be greater than 1", NumberOfRoles > 1);
    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles - 1);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(1));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertFalse(permissionCheckResults.get(i).isPermitted());
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceNotEnabledAndRoleExistingAndMatching() {
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(false));

    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(NumberOfRoles));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertFalse(permissionCheckResults.get(i).isPermitted());
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCheckAccessForSupportServiceNotEnabledAndRoleExistingAndSomeMatching() {
    assertTrue("For this testcase, number of roles should be greater than 3", NumberOfRoles > 3);
    when(supportService.fetchSupportPreference(any())).thenReturn(buildSupportPreference(false));

    PrivilegedAccessCheck privilegedAccessCheck = buildPrivilegedAccessCheck(NumberOfRoles - 1);
    when(privilegedRoleAssignmentDao.getByPrincipal(any())).thenReturn(buildPrivilegedRoleAssignments(3));

    PrivilegedAccessResult privilegedAccessResult = privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);

    verify(supportService, times(1)).fetchSupportPreference(any());
    verify(privilegedRoleAssignmentService, times(1)).checkAccess(privilegedAccessCheck);

    List<PermissionCheckResult> permissionCheckResults = privilegedAccessResult.getPermissionCheckResults();
    List<PermissionCheck> permissionChecks = privilegedAccessCheck.getPermissionChecks();
    assertEquals(privilegedAccessResult.getAccountIdentifier(), privilegedAccessCheck.getAccountIdentifier());
    assertEquals(privilegedAccessResult.getPrincipal(), privilegedAccessCheck.getPrincipal());
    assertEquals(permissionCheckResults.size(), permissionChecks.size());

    for (int i = 0; i < permissionChecks.size(); i++) {
      assertEquals(permissionChecks.get(i).getResourceType(), permissionCheckResults.get(i).getResourceType());
      assertEquals(permissionChecks.get(i).getPermission(), permissionCheckResults.get(i).getPermission());
      assertFalse(permissionCheckResults.get(i).isPermitted());
    }
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSyncRoleAssignmentsWhenNoIntersection() {
    Set<Principal> testSavedPrincipals = new HashSet<>();
    Set<Principal> testUpdatedPrincipals = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      testSavedPrincipals.add(testPrincipalList.get(i));
    }
    for (int i = 3; i < 6; i++) {
      testUpdatedPrincipals.add(testPrincipalList.get(i));
    }

    Set<Principal> testAddedPrincipals = Sets.difference(testUpdatedPrincipals, testSavedPrincipals);

    List<PrivilegedRoleAssignment> testSavedRoleAssignments =
        testSavedPrincipals.stream()
            .map(principal
                -> PrivilegedRoleAssignment.builder()
                       .principalType(principal.getPrincipalType())
                       .principalIdentifier(principal.getPrincipalIdentifier())
                       .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                       .build())
            .collect(Collectors.toList());
    List<PrivilegedRoleAssignment> testNewRoleAssignments =
        testAddedPrincipals.stream()
            .map(principal
                -> PrivilegedRoleAssignment.builder()
                       .principalIdentifier(principal.getPrincipalIdentifier())
                       .principalType(principal.getPrincipalType())
                       .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                       .build())
            .collect(Collectors.toList());

    when(privilegedRoleAssignmentDao.getByRole(DEMO_ROLE_IDENTIFIER)).thenReturn(testSavedRoleAssignments);

    privilegedRoleAssignmentService.syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);

    verify(privilegedRoleAssignmentService, times(1)).syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).getByRole(DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).removeByPrincipalsAndRole(testSavedPrincipals, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).insertAllIgnoringDuplicates(testNewRoleAssignments);
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSyncRoleAssignmentsWhenFiniteIntersection() {
    Set<Principal> testSavedPrincipals = new HashSet<>();
    Set<Principal> testUpdatedPrincipals = new HashSet<>();
    for (int i = 0; i < 4; i++) {
      testSavedPrincipals.add(testPrincipalList.get(i));
    }
    for (int i = 2; i < 6; i++) {
      testUpdatedPrincipals.add(testPrincipalList.get(i));
    }
    Set<Principal> testRemovedPrincipals = Sets.difference(testSavedPrincipals, testUpdatedPrincipals);
    Set<Principal> testAddedPrincipals = Sets.difference(testUpdatedPrincipals, testSavedPrincipals);

    List<PrivilegedRoleAssignment> testSavedRoleAssignments =
        testSavedPrincipals.stream()
            .map(principal
                -> PrivilegedRoleAssignment.builder()
                       .principalType(principal.getPrincipalType())
                       .principalIdentifier(principal.getPrincipalIdentifier())
                       .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                       .build())
            .collect(Collectors.toList());
    List<PrivilegedRoleAssignment> testNewRoleAssignments =
        testAddedPrincipals.stream()
            .map(principal
                -> PrivilegedRoleAssignment.builder()
                       .principalIdentifier(principal.getPrincipalIdentifier())
                       .principalType(principal.getPrincipalType())
                       .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                       .build())
            .collect(Collectors.toList());

    when(privilegedRoleAssignmentDao.getByRole(DEMO_ROLE_IDENTIFIER)).thenReturn(testSavedRoleAssignments);

    privilegedRoleAssignmentService.syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);

    verify(privilegedRoleAssignmentService, times(1)).syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).getByRole(DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1))
        .removeByPrincipalsAndRole(testRemovedPrincipals, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).insertAllIgnoringDuplicates(testNewRoleAssignments);
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSyncRoleAssignmentsWhenUpdatedInSaved() {
    Set<Principal> testSavedPrincipals = new HashSet<>();
    Set<Principal> testUpdatedPrincipals = new HashSet<>();
    for (int i = 0; i < 6; i++) {
      testSavedPrincipals.add(testPrincipalList.get(i));
    }
    for (int i = 2; i < 6; i++) {
      testUpdatedPrincipals.add(testPrincipalList.get(i));
    }
    Set<Principal> testRemovedPrincipals = Sets.difference(testSavedPrincipals, testUpdatedPrincipals);

    List<PrivilegedRoleAssignment> testSavedRoleAssignments =
        testSavedPrincipals.stream()
            .map(principal
                -> PrivilegedRoleAssignment.builder()
                       .principalType(principal.getPrincipalType())
                       .principalIdentifier(principal.getPrincipalIdentifier())
                       .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                       .build())
            .collect(Collectors.toList());

    when(privilegedRoleAssignmentDao.getByRole(DEMO_ROLE_IDENTIFIER)).thenReturn(testSavedRoleAssignments);

    privilegedRoleAssignmentService.syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);

    verify(privilegedRoleAssignmentService, times(1)).syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).getByRole(DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1))
        .removeByPrincipalsAndRole(testRemovedPrincipals, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(0)).insertAllIgnoringDuplicates(any());
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSyncRoleAssignmentsWhenSavedInUpdated() {
    Set<Principal> testSavedPrincipals = new HashSet<>();
    Set<Principal> testUpdatedPrincipals = new HashSet<>();
    for (int i = 0; i < 4; i++) {
      testSavedPrincipals.add(testPrincipalList.get(i));
    }
    for (int i = 0; i < 6; i++) {
      testUpdatedPrincipals.add(testPrincipalList.get(i));
    }
    Set<Principal> testAddedPrincipals = Sets.difference(testUpdatedPrincipals, testSavedPrincipals);

    List<PrivilegedRoleAssignment> testSavedRoleAssignments =
        testSavedPrincipals.stream()
            .map(principal
                -> PrivilegedRoleAssignment.builder()
                       .principalType(principal.getPrincipalType())
                       .principalIdentifier(principal.getPrincipalIdentifier())
                       .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                       .build())
            .collect(Collectors.toList());
    List<PrivilegedRoleAssignment> testNewRoleAssignments =
        testAddedPrincipals.stream()
            .map(principal
                -> PrivilegedRoleAssignment.builder()
                       .principalIdentifier(principal.getPrincipalIdentifier())
                       .principalType(principal.getPrincipalType())
                       .roleIdentifier(DEMO_ROLE_IDENTIFIER)
                       .build())
            .collect(Collectors.toList());

    when(privilegedRoleAssignmentDao.getByRole(DEMO_ROLE_IDENTIFIER)).thenReturn(testSavedRoleAssignments);

    privilegedRoleAssignmentService.syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);

    verify(privilegedRoleAssignmentService, times(1)).syncRoleAssignments(testUpdatedPrincipals, DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(1)).getByRole(DEMO_ROLE_IDENTIFIER);
    verify(privilegedRoleAssignmentDao, times(0)).removeByPrincipalsAndRole(any(), any());
    verify(privilegedRoleAssignmentDao, times(1)).insertAllIgnoringDuplicates(testNewRoleAssignments);
    verifyNoMoreInteractions(privilegedRoleAssignmentService);
  }
}
