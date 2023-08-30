/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountService;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupService;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.users.HarnessUserService;
import io.harness.accesscontrol.principals.users.UserService;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.v1.model.Principal;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignment;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignmentResponse;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;

@OwnedBy(PL)
public class RoleAssignmentApiUtilsTest extends AccessControlTestBase {
  @Mock private HarnessResourceGroupService harnessResourceGroupService;
  @Mock private HarnessUserGroupService harnessUserGroupService;
  @Mock private HarnessUserService harnessUserService;
  @Mock private HarnessServiceAccountService harnessServiceAccountService;
  @Mock private HarnessScopeService harnessScopeService;
  @Mock private ScopeService scopeService;
  @Mock private ResourceGroupService resourceGroupService;
  @Mock private UserGroupService userGroupService;
  @Mock private UserService userService;
  @Mock private ServiceAccountService serviceAccountService;
  @Mock private RoleAssignmentDTOMapper roleAssignmentDTOMapper;
  @Mock private AccessControlClient accessControlClient;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  private RoleAssignmentApiUtils roleAssignmentApiUtils;
  private static final String IDENTIFIER = "identifier";
  private static final String ROLE_IDENTIFIER = "role_identifier";
  private static final String RESOURCE_GROUP_IDENTIFIER = "resource_group_identifier";
  private static final String PRINCIPAL_IDENTIFIER = "principal_identifier";
  private static final String SCOPE_LEVEL = "ACCOUNT";
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";

  private static final String ACCOUNT_ID = randomAlphabetic(10);
  private static final String ORG_ID = randomAlphabetic(10);
  private static final String PROJECT_ID = randomAlphabetic(10);
  private static final String ROLE_ASSIGNMENT_ID = randomAlphabetic(10);
  private static final String PRINCIPAL_ID = randomAlphabetic(10);
  private static final String PROJECT_SCOPE_IDENTIFIER =
      "/ACCOUNT/" + ACCOUNT_ID + "/ORGANIZATION/" + ORG_ID + "/PROJECT/" + PROJECT_ID;

  HarnessScopeParams harnessProjectScopeParams = HarnessScopeParams.builder()
                                                     .accountIdentifier(ACCOUNT_ID)
                                                     .orgIdentifier(ORG_ID)
                                                     .projectIdentifier(PROJECT_ID)
                                                     .build();

  @Before
  public void setup() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    roleAssignmentApiUtils =
        new RoleAssignmentApiUtils(factory.getValidator(), harnessResourceGroupService, harnessUserGroupService,
            harnessUserService, harnessServiceAccountService, harnessScopeService, scopeService, resourceGroupService,
            userGroupService, userService, serviceAccountService, roleAssignmentDTOMapper, accessControlClient);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetRoleAssignmentDto() {
    Principal principal = new Principal();
    principal.setIdentifier(PRINCIPAL_IDENTIFIER);
    principal.setScopeLevel(SCOPE_LEVEL);
    principal.setType(Principal.TypeEnum.USER);

    RoleAssignment request = new RoleAssignment();
    request.setIdentifier(IDENTIFIER);
    request.setRole(ROLE_IDENTIFIER);
    request.setResourceGroup(RESOURCE_GROUP_IDENTIFIER);
    request.setDisabled(true);
    request.setManaged(true);
    request.setPrincipal(principal);

    RoleAssignmentDTO roleAssignmentDto = roleAssignmentApiUtils.getRoleAssignmentDto(request);

    assertThat(roleAssignmentDto.getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(roleAssignmentDto.getRoleIdentifier()).isEqualTo(ROLE_IDENTIFIER);
    assertThat(roleAssignmentDto.getResourceGroupIdentifier()).isEqualTo(RESOURCE_GROUP_IDENTIFIER);
    assertThat(roleAssignmentDto.getPrincipal().getIdentifier()).isEqualTo(PRINCIPAL_IDENTIFIER);
    assertThat(roleAssignmentDto.getPrincipal().getScopeLevel()).isEqualTo(SCOPE_LEVEL);
    assertThat(roleAssignmentDto.getPrincipal().getType()).isEqualTo(PrincipalType.USER);
    assertThat(roleAssignmentDto.isDisabled()).isEqualTo(true);
    assertThat(roleAssignmentDto.isManaged()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetRoleAssignmentResponse() {
    PrincipalDTO principalDTO = PrincipalDTO.builder()
                                    .type(PrincipalType.USER)
                                    .identifier(PRINCIPAL_IDENTIFIER)
                                    .scopeLevel(SCOPE_LEVEL)
                                    .build();

    RoleAssignmentDTO roleAssignmentDto = RoleAssignmentDTO.builder()
                                              .identifier(IDENTIFIER)
                                              .roleIdentifier(ROLE_IDENTIFIER)
                                              .resourceGroupIdentifier(RESOURCE_GROUP_IDENTIFIER)
                                              .managed(true)
                                              .disabled(true)
                                              .principal(principalDTO)
                                              .build();

    RoleAssignmentResponseDTO responseDto =
        RoleAssignmentResponseDTO.builder()
            .roleAssignment(roleAssignmentDto)
            .createdAt(1234567890L)
            .lastModifiedAt(1234567890L)
            .harnessManaged(true)
            .scope(ScopeDTO.builder().accountIdentifier(ACCOUNT).orgIdentifier(ORG).projectIdentifier(PROJECT).build())
            .build();

    RoleAssignmentResponse roleAssignmentResponse = roleAssignmentApiUtils.getRoleAssignmentResponse(responseDto);

    assertThat(roleAssignmentResponse.getCreated()).isEqualTo(1234567890L);
    assertThat(roleAssignmentResponse.getUpdated()).isEqualTo(1234567890L);
    assertThat(roleAssignmentResponse.getRoleAssignment().getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(roleAssignmentResponse.getRoleAssignment().getRole()).isEqualTo(ROLE_IDENTIFIER);
    assertThat(roleAssignmentResponse.getRoleAssignment().getResourceGroup()).isEqualTo(RESOURCE_GROUP_IDENTIFIER);
    assertThat(roleAssignmentResponse.getRoleAssignment().isDisabled()).isEqualTo(true);
    assertThat(roleAssignmentResponse.getRoleAssignment().isManaged()).isEqualTo(true);
    assertThat(roleAssignmentResponse.getRoleAssignment().getPrincipal().getIdentifier())
        .isEqualTo(PRINCIPAL_IDENTIFIER);
    assertThat(roleAssignmentResponse.getRoleAssignment().getPrincipal().getType()).isEqualTo(Principal.TypeEnum.USER);
    assertThat(roleAssignmentResponse.getRoleAssignment().getPrincipal().getScopeLevel()).isEqualTo(SCOPE_LEVEL);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void buildRoleAssignmentWithPrincipalScopeLevel_whenPrincipalScopeIsNull() {
    io.harness.accesscontrol.roleassignments.RoleAssignment.RoleAssignmentBuilder roleAssignmentBuilder =
        io.harness.accesscontrol.roleassignments.RoleAssignment.builder()
            .identifier(ROLE_ASSIGNMENT_ID)
            .scopeIdentifier(PROJECT_SCOPE_IDENTIFIER)
            .scopeLevel("project")
            .resourceGroupIdentifier("_all_project_level_resources")
            .roleIdentifier("_project_basic")
            .principalIdentifier(PRINCIPAL_ID)
            .principalType(PrincipalType.USER_GROUP)
            .managed(false)
            .disabled(false)
            .createdAt(null)
            .lastModifiedAt(null);

    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignmentParam = roleAssignmentBuilder.build();
    io.harness.accesscontrol.roleassignments.RoleAssignment expectedRoleAssigment =
        roleAssignmentBuilder.principalScopeLevel(PROJECT).build();

    Scope scope = fromParams(harnessProjectScopeParams);

    io.harness.accesscontrol.roleassignments.RoleAssignment resultRoleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(roleAssignmentParam, scope);
    assertThat(resultRoleAssignment).isEqualToComparingFieldByField(expectedRoleAssigment);
    assertThat(resultRoleAssignment.getPrincipalScopeLevel()).isEqualTo(PROJECT);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void buildRoleAssignmentWithPrincipalScopeLevel_whenPrincipalScopeIsNull_forServiceAccount() {
    io.harness.accesscontrol.roleassignments.RoleAssignment.RoleAssignmentBuilder roleAssignmentBuilder =
        io.harness.accesscontrol.roleassignments.RoleAssignment.builder()
            .identifier(ROLE_ASSIGNMENT_ID)
            .scopeIdentifier(PROJECT_SCOPE_IDENTIFIER)
            .scopeLevel("project")
            .resourceGroupIdentifier("_all_project_level_resources")
            .roleIdentifier("_project_basic")
            .principalIdentifier(PRINCIPAL_ID)
            .principalType(PrincipalType.SERVICE_ACCOUNT)
            .managed(false)
            .disabled(false)
            .createdAt(null)
            .lastModifiedAt(null);

    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignmentParam = roleAssignmentBuilder.build();
    io.harness.accesscontrol.roleassignments.RoleAssignment expectedRoleAssigment =
        roleAssignmentBuilder.principalScopeLevel(PROJECT).build();

    Scope scope = fromParams(harnessProjectScopeParams);

    io.harness.accesscontrol.roleassignments.RoleAssignment resultRoleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(roleAssignmentParam, scope);
    assertThat(resultRoleAssignment).isEqualToComparingFieldByField(expectedRoleAssigment);
    assertThat(resultRoleAssignment.getPrincipalScopeLevel()).isEqualTo(PROJECT);
    verify(harnessServiceAccountService, times(1)).sync(PRINCIPAL_ID, scope);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void buildRoleAssignmentWithPrincipalScopeLevel_whenPrincipalScopeSpecified() {
    io.harness.accesscontrol.roleassignments.RoleAssignment.RoleAssignmentBuilder roleAssignmentBuilder =
        io.harness.accesscontrol.roleassignments.RoleAssignment.builder()
            .identifier(ROLE_ASSIGNMENT_ID)
            .scopeIdentifier(PROJECT_SCOPE_IDENTIFIER)
            .scopeLevel("project")
            .resourceGroupIdentifier("_all_project_level_resources")
            .roleIdentifier("_project_basic")
            .principalScopeLevel(PROJECT)
            .principalIdentifier(PRINCIPAL_ID)
            .principalType(PrincipalType.SERVICE_ACCOUNT)
            .managed(false)
            .disabled(false)
            .createdAt(null)
            .lastModifiedAt(null);

    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignmentParam = roleAssignmentBuilder.build();
    io.harness.accesscontrol.roleassignments.RoleAssignment expectedRoleAssigment = roleAssignmentBuilder.build();

    Scope scope = fromParams(harnessProjectScopeParams);

    io.harness.accesscontrol.roleassignments.RoleAssignment resultRoleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(roleAssignmentParam, scope);
    assertThat(resultRoleAssignment).isEqualToComparingFieldByField(expectedRoleAssigment);
    assertThat(resultRoleAssignment.getPrincipalScopeLevel()).isEqualTo(PROJECT);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void buildRoleAssignmentWithPrincipalScopeLevel_whenPrincipalScopeIsDifferentFromRAScope() {
    io.harness.accesscontrol.roleassignments.RoleAssignment.RoleAssignmentBuilder roleAssignmentBuilder =
        io.harness.accesscontrol.roleassignments.RoleAssignment.builder()
            .identifier(ROLE_ASSIGNMENT_ID)
            .scopeIdentifier(PROJECT_SCOPE_IDENTIFIER)
            .scopeLevel("project")
            .resourceGroupIdentifier("_all_project_level_resources")
            .roleIdentifier("_project_basic")
            .principalScopeLevel(ACCOUNT)
            .principalIdentifier(PRINCIPAL_ID)
            .principalType(PrincipalType.SERVICE_ACCOUNT)
            .managed(false)
            .disabled(false)
            .createdAt(null)
            .lastModifiedAt(null);

    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignmentParam = roleAssignmentBuilder.build();
    io.harness.accesscontrol.roleassignments.RoleAssignment expectedRoleAssigment = roleAssignmentBuilder.build();

    Scope scope = fromParams(harnessProjectScopeParams);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(
        "Cannot create role assignment for given Service Account. Principal should be of same scope as of role assignment.");

    io.harness.accesscontrol.roleassignments.RoleAssignment resultRoleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(roleAssignmentParam, scope);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void buildRoleAssignmentWithPrincipalScopeLevel_whenPrincipalScopeIsNull_withUerGroupPrincipal() {
    io.harness.accesscontrol.roleassignments.RoleAssignment.RoleAssignmentBuilder roleAssignmentBuilder =
        io.harness.accesscontrol.roleassignments.RoleAssignment.builder()
            .identifier(ROLE_ASSIGNMENT_ID)
            .scopeIdentifier(PROJECT_SCOPE_IDENTIFIER)
            .scopeLevel("project")
            .resourceGroupIdentifier("_all_project_level_resources")
            .roleIdentifier("_project_basic")
            .principalIdentifier(PRINCIPAL_ID)
            .principalType(PrincipalType.USER_GROUP)
            .managed(false)
            .disabled(false)
            .createdAt(null)
            .lastModifiedAt(null);

    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignmentParam = roleAssignmentBuilder.build();
    io.harness.accesscontrol.roleassignments.RoleAssignment expectedRoleAssigment =
        roleAssignmentBuilder.principalScopeLevel(PROJECT).build();

    Scope scope = fromParams(harnessProjectScopeParams);

    io.harness.accesscontrol.roleassignments.RoleAssignment resultRoleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(roleAssignmentParam, scope);
    assertThat(resultRoleAssignment).isEqualToComparingFieldByField(expectedRoleAssigment);
    assertThat(resultRoleAssignment.getPrincipalScopeLevel()).isEqualTo(PROJECT);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void buildRoleAssignmentWithPrincipalScopeLevel_withUerGroupPrincipal() {
    io.harness.accesscontrol.roleassignments.RoleAssignment.RoleAssignmentBuilder roleAssignmentBuilder =
        io.harness.accesscontrol.roleassignments.RoleAssignment.builder()
            .identifier(ROLE_ASSIGNMENT_ID)
            .scopeIdentifier(PROJECT_SCOPE_IDENTIFIER)
            .scopeLevel("project")
            .resourceGroupIdentifier("_all_project_level_resources")
            .roleIdentifier("_project_basic")
            .principalScopeLevel(PROJECT)
            .principalIdentifier(PRINCIPAL_ID)
            .principalType(PrincipalType.USER_GROUP)
            .managed(false)
            .disabled(false)
            .createdAt(null)
            .lastModifiedAt(null);

    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignmentParam = roleAssignmentBuilder.build();
    io.harness.accesscontrol.roleassignments.RoleAssignment expectedRoleAssigment = roleAssignmentBuilder.build();

    Scope scope = fromParams(harnessProjectScopeParams);
    io.harness.accesscontrol.roleassignments.RoleAssignment resultRoleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(roleAssignmentParam, scope);
    assertThat(resultRoleAssignment).isEqualToComparingFieldByField(expectedRoleAssigment);
    assertThat(resultRoleAssignment.getPrincipalScopeLevel()).isEqualTo(PROJECT);
  }
}
