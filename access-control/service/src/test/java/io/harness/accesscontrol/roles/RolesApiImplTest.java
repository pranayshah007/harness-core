/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.utils.PageTestUtils.getPage;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.roles.api.AccountRolesApiImpl;
import io.harness.accesscontrol.roles.api.OrgRolesApiImpl;
import io.harness.accesscontrol.roles.api.ProjectRolesApiImpl;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.roles.api.RolesApiUtils;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.v1.model.CreateRoleRequest;
import io.harness.spec.server.accesscontrol.v1.model.RolesResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class RolesApiImplTest extends CategoryTest {
  private RoleService roleService;
  private ScopeService scopeService;
  private RoleDTOMapper roleDTOMapper;
  private AccessControlClient accessControlClient;
  private AccountRolesApiImpl accountRolesApi;
  private OrgRolesApiImpl orgRolesApi;
  private ProjectRolesApiImpl projectRolesApi;
  private RolesApiUtils rolesApiUtils;
  private Validator validator;

  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String scopeIdentifierAcc = String.format("/ACCOUNT/%s", account);
  String scopeIdentifierOrg = String.format("%s/ORGANIZATION/%s", scopeIdentifierAcc, org);
  String scopeIdentifierProject = String.format("%s/PROJECT/%s", scopeIdentifierOrg, project);
  int page = 0;
  int limit = 1;

  @Before
  public void setup() {
    roleService = mock(RoleService.class);
    scopeService = mock(ScopeService.class);
    roleDTOMapper = new RoleDTOMapper(scopeService);
    accessControlClient = mock(AccessControlClient.class);
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    rolesApiUtils = new RolesApiUtils(validator);
    accountRolesApi =
        new AccountRolesApiImpl(roleService, scopeService, roleDTOMapper, accessControlClient, rolesApiUtils);
    orgRolesApi = new OrgRolesApiImpl(roleService, scopeService, roleDTOMapper, accessControlClient, rolesApiUtils);
    projectRolesApi =
        new ProjectRolesApiImpl(roleService, scopeService, roleDTOMapper, accessControlClient, rolesApiUtils);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleCreate() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(name);
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.getOrCreate(scope)).thenReturn(scope);

    RoleDTO roleDTO = rolesApiUtils.getRoleAccDTO(request);
    Role role = RoleDTOMapper.fromDTO(scope.toString(), roleDTO);
    when(roleService.create(role)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = accountRolesApi.createRoleAcc(request, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(scopeService, times(1)).getOrCreate(any());
    verify(roleService, times(1)).create(role);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleDelete() {
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierAcc)).thenReturn(scope);

    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierAcc).build();
    when(roleService.delete(identifier, scopeIdentifierAcc)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = accountRolesApi.deleteRoleAcc(identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).delete(identifier, scopeIdentifierAcc);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleGet() {
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierAcc)).thenReturn(scope);
    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierAcc).build();
    when(roleService.get(identifier, scopeIdentifierAcc, NO_FILTER)).thenReturn(Optional.ofNullable(role));

    Response response = accountRolesApi.getRoleAcc(identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleList() {
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierAcc)).thenReturn(scope);
    String searchTerm = randomAlphabetic(10);
    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierAcc).build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .searchTerm(searchTerm)
                                .scopeIdentifier(scopeIdentifierAcc)
                                .managedFilter(NO_FILTER)
                                .build();

    when(roleService.list(any(), any(), eq(true)))
        .thenReturn(getNGPageResponse(getPage(Collections.singletonList(role), 1)));

    Response response = accountRolesApi.listRolesAcc(page, limit, searchTerm, account, "identifier", "ASC");
    List<RolesResponse> entity = (List<RolesResponse>) response.getEntity();

    assertEquals(searchTerm, roleFilter.getSearchTerm());
    assertEquals(3, response.getHeaders().size());
    assertEquals(1, entity.size());
    assertEquals(identifier, entity.get(0).getIdentifier());
    assertEquals(name, entity.get(0).getName());
    assertEquals(account, entity.get(0).getScope().getAccount());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleUpdate() {
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierAcc)).thenReturn(scope);
    String updatedName = randomAlphabetic(10);
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(updatedName);
    RoleDTO roleDTO = rolesApiUtils.getRoleAccDTO(request);

    Role role = RoleDTOMapper.fromDTO(scope.toString(), roleDTO);
    when(roleService.update(role)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = accountRolesApi.updateRoleAcc(request, identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(updatedName, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).update(role);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleCreate() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(name);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.getOrCreate(scope)).thenReturn(scope);

    RoleDTO roleDTO = rolesApiUtils.getRoleOrgDTO(request);
    Role role = RoleDTOMapper.fromDTO(scope.toString(), roleDTO);
    when(roleService.create(role)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = orgRolesApi.createRoleOrg(request, org, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(scopeService, times(1)).getOrCreate(any());
    verify(roleService, times(1)).create(role);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleDelete() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierOrg)).thenReturn(scope);

    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierOrg).build();

    when(roleService.delete(identifier, scopeIdentifierOrg)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    Response response = orgRolesApi.deleteRoleOrg(org, identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).delete(identifier, scopeIdentifierOrg);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleGet() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierOrg)).thenReturn(scope);
    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierOrg).build();
    when(roleService.get(identifier, scopeIdentifierOrg, NO_FILTER)).thenReturn(Optional.ofNullable(role));

    Response response = orgRolesApi.getRoleOrg(org, identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleList() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierOrg)).thenReturn(scope);
    String searchTerm = randomAlphabetic(10);
    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierOrg).build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .searchTerm(searchTerm)
                                .scopeIdentifier(scopeIdentifierOrg)
                                .managedFilter(NO_FILTER)
                                .build();

    when(roleService.list(any(), any(), eq(true)))
        .thenReturn(getNGPageResponse(getPage(Collections.singletonList(role), 1)));

    Response response = orgRolesApi.listRolesOrg(org, page, limit, searchTerm, account, "name", "desc");
    List<RolesResponse> entity = (List<RolesResponse>) response.getEntity();

    assertEquals(searchTerm, roleFilter.getSearchTerm());
    assertEquals(3, response.getHeaders().size());
    assertEquals(1, entity.size());
    assertEquals(identifier, entity.get(0).getIdentifier());
    assertEquals(name, entity.get(0).getName());
    assertEquals(account, entity.get(0).getScope().getAccount());
    assertEquals(org, entity.get(0).getScope().getOrg());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleUpdate() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierOrg)).thenReturn(scope);
    String updatedName = randomAlphabetic(10);
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(updatedName);
    RoleDTO roleDTO = rolesApiUtils.getRoleOrgDTO(request);
    Role role = RoleDTOMapper.fromDTO(scope.toString(), roleDTO);
    when(roleService.update(role)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = orgRolesApi.updateRoleOrg(request, org, identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(updatedName, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).update(role);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleCreate() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(name);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.getOrCreate(scope)).thenReturn(scope);

    RoleDTO roleDTO = rolesApiUtils.getRoleProjectDTO(request);
    Role role = RoleDTOMapper.fromDTO(scope.toString(), roleDTO);
    when(roleService.create(role)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = projectRolesApi.createRoleProject(request, org, project, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    assertEquals(project, entity.getScope().getProject());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(scopeService, times(1)).getOrCreate(any());
    verify(roleService, times(1)).create(role);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleDelete() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierProject)).thenReturn(scope);

    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierProject).build();
    when(roleService.delete(identifier, scopeIdentifierProject)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = projectRolesApi.deleteRoleProject(org, project, identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    assertEquals(project, entity.getScope().getProject());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).delete(identifier, scopeIdentifierProject);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleGet() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierProject)).thenReturn(scope);
    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierProject).build();
    when(roleService.get(identifier, scopeIdentifierProject, NO_FILTER)).thenReturn(Optional.ofNullable(role));

    Response response = projectRolesApi.getRoleProject(org, project, identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    assertEquals(project, entity.getScope().getProject());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleList() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierProject)).thenReturn(scope);
    String searchTerm = randomAlphabetic(10);
    Role role = Role.builder().identifier(identifier).name(name).scopeIdentifier(scopeIdentifierProject).build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .searchTerm(searchTerm)
                                .scopeIdentifier(scopeIdentifierProject)
                                .managedFilter(NO_FILTER)
                                .build();

    when(roleService.list(any(), any(), eq(true)))
        .thenReturn(getNGPageResponse(getPage(Collections.singletonList(role), 1)));

    Response response =
        projectRolesApi.listRolesProject(org, project, page, limit, searchTerm, account, "updated", "asc");
    List<RolesResponse> entity = (List<RolesResponse>) response.getEntity();

    assertEquals(searchTerm, roleFilter.getSearchTerm());
    assertEquals(3, response.getHeaders().size());
    assertEquals(1, entity.size());
    assertEquals(identifier, entity.get(0).getIdentifier());
    assertEquals(name, entity.get(0).getName());
    assertEquals(account, entity.get(0).getScope().getAccount());
    assertEquals(org, entity.get(0).getScope().getOrg());
    assertEquals(project, entity.get(0).getScope().getProject());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleUpdate() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierProject)).thenReturn(scope);
    String updatedName = randomAlphabetic(10);
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(updatedName);
    RoleDTO roleDTO = rolesApiUtils.getRoleProjectDTO(request);
    Role role = RoleDTOMapper.fromDTO(scope.toString(), roleDTO);
    when(roleService.update(role)).thenReturn(role);
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);

    Response response = projectRolesApi.updateRoleProject(request, org, project, identifier, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(identifier, entity.getIdentifier());
    assertEquals(updatedName, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    assertEquals(project, entity.getScope().getProject());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).update(role);
  }
}