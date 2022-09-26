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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.roles.api.AccountRolesApiImpl;
import io.harness.accesscontrol.roles.api.OrgRolesApiImpl;
import io.harness.accesscontrol.roles.api.ProjectRolesApiImpl;
import io.harness.accesscontrol.roles.api.RoleApiUtils;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.model.CreateRoleRequest;
import io.harness.spec.server.accesscontrol.model.RolesResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class RolesApiImplTest extends CategoryTest {
  private RoleService roleService;
  private ScopeService scopeService;
  private RoleDTOMapper roleDTOMapper;
  private TransactionTemplate transactionTemplate;
  private AccessControlClient accessControlClient;
  private AccountRolesApiImpl accountRolesApi;
  private OrgRolesApiImpl orgRolesApi;
  private ProjectRolesApiImpl projectRolesApi;

  String slug = randomAlphabetic(10);
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
    transactionTemplate = mock(TransactionTemplate.class);
    OutboxService outboxService = mock(OutboxService.class);
    accessControlClient = mock(AccessControlClient.class);

    accountRolesApi = new AccountRolesApiImpl(
        roleService, scopeService, roleDTOMapper, transactionTemplate, outboxService, accessControlClient);
    orgRolesApi = new OrgRolesApiImpl(
        roleService, scopeService, roleDTOMapper, transactionTemplate, outboxService, accessControlClient);
    projectRolesApi = new ProjectRolesApiImpl(
        roleService, scopeService, roleDTOMapper, transactionTemplate, outboxService, accessControlClient);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleCreate() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setSlug(slug);
    request.setName(name);
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.getOrCreate(scope)).thenReturn(scope);

    RoleDTO roleDTO = RoleApiUtils.getRoleAccDTO(request);
    RoleResponseDTO roleResponseDTO =
        RoleResponseDTO.builder().role(roleDTO).scope(ScopeDTO.builder().accountIdentifier(account).build()).build();
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(roleResponseDTO);
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = accountRolesApi.createRoleAcc(request, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(scopeService, times(1)).getOrCreate(any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleDelete() {
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierAcc)).thenReturn(scope);

    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierAcc).build();
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(roleDTOMapper.toResponseDTO(role));
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = accountRolesApi.deleteRoleAcc(slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRoleGet() {
    Scope scope = Scope.builder().instanceId(account).level(HarnessScopeLevel.ACCOUNT).build();
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierAcc)).thenReturn(scope);
    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierAcc).build();
    when(roleService.get(slug, scopeIdentifierAcc, NO_FILTER)).thenReturn(Optional.ofNullable(role));

    Response response = accountRolesApi.getRoleAcc(slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
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
    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierAcc).build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .searchTerm(searchTerm)
                                .scopeIdentifier(scopeIdentifierAcc)
                                .managedFilter(NO_FILTER)
                                .build();

    when(roleService.list(RoleApiUtils.getPageRequest(page, limit), roleFilter))
        .thenReturn(getNGPageResponse(getPage(Collections.singletonList(role), 1)));

    Response response = accountRolesApi.listRolesAcc(account, page, limit, searchTerm);
    List<RolesResponse> entity = (List<RolesResponse>) response.getEntity();

    assertEquals(searchTerm, roleFilter.getSearchTerm());
    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(slug, entity.get(0).getSlug());
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
    request.setSlug(slug);
    request.setName(updatedName);
    RoleDTO roleDTO = RoleApiUtils.getRoleAccDTO(request);
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(
        RoleResponseDTO.builder().role(roleDTO).scope(ScopeDTO.builder().accountIdentifier(account).build()).build());
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = accountRolesApi.updateRoleAcc(request, slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(updatedName, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleCreate() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setSlug(slug);
    request.setName(name);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.getOrCreate(scope)).thenReturn(scope);

    RoleDTO roleDTO = RoleApiUtils.getRoleOrgDTO(request);
    RoleResponseDTO roleResponseDTO =
        RoleResponseDTO.builder()
            .role(roleDTO)
            .scope(ScopeDTO.builder().accountIdentifier(account).orgIdentifier(org).build())
            .build();
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(roleResponseDTO);
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = orgRolesApi.createRoleOrg(request, org, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(scopeService, times(1)).getOrCreate(any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleDelete() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierOrg)).thenReturn(scope);

    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierOrg).build();
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(roleDTOMapper.toResponseDTO(role));
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = orgRolesApi.deleteRoleOrg(org, slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRoleGet() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierOrg)).thenReturn(scope);
    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierOrg).build();
    when(roleService.get(slug, scopeIdentifierOrg, NO_FILTER)).thenReturn(Optional.ofNullable(role));

    Response response = orgRolesApi.getRoleOrg(org, slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
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
    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierOrg).build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .searchTerm(searchTerm)
                                .scopeIdentifier(scopeIdentifierOrg)
                                .managedFilter(NO_FILTER)
                                .build();

    when(roleService.list(RoleApiUtils.getPageRequest(page, limit), roleFilter))
        .thenReturn(getNGPageResponse(getPage(Collections.singletonList(role), 1)));

    Response response = orgRolesApi.listRolesOrg(org, account, page, limit, searchTerm);
    List<RolesResponse> entity = (List<RolesResponse>) response.getEntity();

    assertEquals(searchTerm, roleFilter.getSearchTerm());
    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(slug, entity.get(0).getSlug());
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
    request.setSlug(slug);
    request.setName(updatedName);
    RoleDTO roleDTO = RoleApiUtils.getRoleOrgDTO(request);
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(
        RoleResponseDTO.builder()
            .role(roleDTO)
            .scope(ScopeDTO.builder().accountIdentifier(account).orgIdentifier(org).build())
            .build());
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = orgRolesApi.updateRoleOrg(request, org, slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(updatedName, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleCreate() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setSlug(slug);
    request.setName(name);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.getOrCreate(scope)).thenReturn(scope);

    RoleDTO roleDTO = RoleApiUtils.getRoleProjectDTO(request);
    RoleResponseDTO roleResponseDTO =
        RoleResponseDTO.builder()
            .role(roleDTO)
            .scope(ScopeDTO.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build())
            .build();
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(roleResponseDTO);
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = projectRolesApi.createRoleProject(request, org, project, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    assertEquals(project, entity.getScope().getProject());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(scopeService, times(1)).getOrCreate(any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleDelete() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierProject)).thenReturn(scope);

    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierProject).build();
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(roleDTOMapper.toResponseDTO(role));
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = projectRolesApi.deleteRoleProject(org, project, slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(name, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    assertEquals(project, entity.getScope().getProject());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRoleGet() {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifierProject)).thenReturn(scope);
    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierProject).build();
    when(roleService.get(slug, scopeIdentifierProject, NO_FILTER)).thenReturn(Optional.ofNullable(role));

    Response response = projectRolesApi.getRoleProject(org, project, slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
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
    Role role = Role.builder().identifier(slug).name(name).scopeIdentifier(scopeIdentifierProject).build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .searchTerm(searchTerm)
                                .scopeIdentifier(scopeIdentifierProject)
                                .managedFilter(NO_FILTER)
                                .build();

    when(roleService.list(RoleApiUtils.getPageRequest(page, limit), roleFilter))
        .thenReturn(getNGPageResponse(getPage(Collections.singletonList(role), 1)));

    Response response = projectRolesApi.listRolesProject(org, project, account, page, limit, searchTerm);
    List<RolesResponse> entity = (List<RolesResponse>) response.getEntity();

    assertEquals(searchTerm, roleFilter.getSearchTerm());
    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(slug, entity.get(0).getSlug());
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
    request.setSlug(slug);
    request.setName(updatedName);
    RoleDTO roleDTO = RoleApiUtils.getRoleProjectDTO(request);
    RolesResponse rolesResponse = RoleApiUtils.getRolesResponse(
        RoleResponseDTO.builder()
            .role(roleDTO)
            .scope(ScopeDTO.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build())
            .build());
    when(transactionTemplate.execute(any())).thenReturn(rolesResponse);

    Response response = projectRolesApi.updateRoleProject(request, org, project, slug, account);
    RolesResponse entity = (RolesResponse) response.getEntity();
    assertEquals(slug, entity.getSlug());
    assertEquals(updatedName, entity.getName());
    assertEquals(account, entity.getScope().getAccount());
    assertEquals(org, entity.getScope().getOrg());
    assertEquals(project, entity.getScope().getProject());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }
}