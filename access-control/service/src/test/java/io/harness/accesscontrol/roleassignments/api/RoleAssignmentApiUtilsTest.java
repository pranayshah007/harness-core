/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.v1.model.Principal;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignment;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignmentResponse;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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

  private RoleAssignmentApiUtils roleAssignmentApiUtils;
  private static final String SLUG = "slug";
  private static final String ROLE_SLUG = "role_slug";
  private static final String RESOURCE_GROUP_SLUG = "resource_group_slug";
  private static final String PRINCIPAL_SLUG = "principal_slug";
  private static final String SCOPE_LEVEL = "ACCOUNT";
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";

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
    principal.setSlug(PRINCIPAL_SLUG);
    principal.setScopeLevel(SCOPE_LEVEL);
    principal.setType(Principal.TypeEnum.USER);

    RoleAssignment request = new RoleAssignment();
    request.setSlug(SLUG);
    request.setRole(ROLE_SLUG);
    request.setResourceGroup(RESOURCE_GROUP_SLUG);
    request.setDisabled(true);
    request.setManaged(true);
    request.setPrincipal(principal);

    RoleAssignmentDTO roleAssignmentDto = roleAssignmentApiUtils.getRoleAssignmentDto(request);

    assertThat(roleAssignmentDto.getIdentifier()).isEqualTo(SLUG);
    assertThat(roleAssignmentDto.getRoleIdentifier()).isEqualTo(ROLE_SLUG);
    assertThat(roleAssignmentDto.getResourceGroupIdentifier()).isEqualTo(RESOURCE_GROUP_SLUG);
    assertThat(roleAssignmentDto.getPrincipal().getIdentifier()).isEqualTo(PRINCIPAL_SLUG);
    assertThat(roleAssignmentDto.getPrincipal().getScopeLevel()).isEqualTo(SCOPE_LEVEL);
    assertThat(roleAssignmentDto.getPrincipal().getType()).isEqualTo(PrincipalType.USER);
    assertThat(roleAssignmentDto.isDisabled()).isEqualTo(true);
    assertThat(roleAssignmentDto.isManaged()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetRoleAssignmentResponse() {
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.USER).identifier(PRINCIPAL_SLUG).scopeLevel(SCOPE_LEVEL).build();

    RoleAssignmentDTO roleAssignmentDto = RoleAssignmentDTO.builder()
                                              .identifier(SLUG)
                                              .roleIdentifier(ROLE_SLUG)
                                              .resourceGroupIdentifier(RESOURCE_GROUP_SLUG)
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
    assertThat(roleAssignmentResponse.getRoleassignment().getSlug()).isEqualTo(SLUG);
    assertThat(roleAssignmentResponse.getRoleassignment().getRole()).isEqualTo(ROLE_SLUG);
    assertThat(roleAssignmentResponse.getRoleassignment().getResourceGroup()).isEqualTo(RESOURCE_GROUP_SLUG);
    assertThat(roleAssignmentResponse.getRoleassignment().isDisabled()).isEqualTo(true);
    assertThat(roleAssignmentResponse.getRoleassignment().isManaged()).isEqualTo(true);
    assertThat(roleAssignmentResponse.getRoleassignment().getPrincipal().getSlug()).isEqualTo(PRINCIPAL_SLUG);
    assertThat(roleAssignmentResponse.getRoleassignment().getPrincipal().getType()).isEqualTo(Principal.TypeEnum.USER);
    assertThat(roleAssignmentResponse.getRoleassignment().getPrincipal().getScopeLevel()).isEqualTo(SCOPE_LEVEL);
  }
}
