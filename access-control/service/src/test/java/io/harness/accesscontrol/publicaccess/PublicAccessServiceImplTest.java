/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import static io.harness.accesscontrol.publicaccess.PublicAccessUtils.PUBLIC_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.publicaccess.PublicAccessUtils.PUBLIC_RESOURCE_GROUP_NAME;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupFactory;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roles.persistence.RoleDBO;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.v1.model.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import retrofit2.Call;

public class PublicAccessServiceImplTest extends AccessControlTestBase {
  @Mock private RoleAssignmentService roleAssignmentService;
  @Mock private ResourceGroupClient resourceGroupClient;
  @Mock private ResourceGroupService resourceGroupService;
  @Mock private ResourceGroupFactory resourceGroupFactory;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private ScopeService scopeService;
  private PublicAccessUtils publicAccessUtil;

  private PublicAccessServiceImpl publicAccessService;

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String ORG_IDENTIFIER = randomAlphabetic(10);
  private static final String PROJECT_IDENTIFIER = randomAlphabetic(10);
  private static final String RESOURCE_TYPE = "PIPELINE";
  private static final String RESOURCE_ID = randomAlphabetic(10);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.publicAccessUtil = new PublicAccessUtils();
    this.publicAccessService = new PublicAccessServiceImpl(roleAssignmentService, resourceGroupClient,
        resourceGroupService, resourceGroupFactory, mongoTemplate, scopeService, this.publicAccessUtil);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testEnablePublicAccess_enablingFirstResourceInAScope() {
    HarnessScopeParams harnessScopeParams = HarnessScopeParams.builder()
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .projectIdentifier(PROJECT_IDENTIFIER)
                                                .build();
    Scope scope = new Scope();
    scope.account(ACCOUNT_IDENTIFIER);
    scope.org(ORG_IDENTIFIER);
    scope.project(PROJECT_IDENTIFIER);
    ResourceType resourceType =
        ResourceType.builder().identifier(RESOURCE_TYPE).isPublic(true).permissionKey(RESOURCE_TYPE).build();
    mockStatic(NGRestUtils.class);
    when(resourceGroupClient.getResourceGroup(
             PUBLIC_RESOURCE_GROUP_IDENTIFIER, scope.getAccount(), scope.getOrg(), scope.getProject()))
        .thenReturn(null);
    when(NGRestUtils.getResponse(null)).thenReturn(null);
    Call<ResponseDTO<ResourceGroupResponse>> request = mock(Call.class);
    when(resourceGroupClient.createResourceGroup(
             eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), any()))
        .thenReturn(request);
    when(NGRestUtils.getResponse(request)).thenReturn(ResourceGroupResponse.builder().build());
    when(scopeService.getOrCreate(ScopeMapper.fromParams(harnessScopeParams)))
        .thenReturn(io.harness.accesscontrol.scopes.core.Scope.builder().level(HarnessScopeLevel.ACCOUNT).build());
    when(mongoTemplate.find(any(), eq(RoleDBO.class)))
        .thenReturn(List.of(RoleDBO.builder().isPublic(true).identifier("_pipeline_allUsers_access_role").build(),
            RoleDBO.builder().isPublic(true).identifier("_pipeline_allAuthenticatedUsers_access_role").build()));
    boolean result = publicAccessService.enable(RESOURCE_ID, resourceType, scope);
    verify(resourceGroupClient, times(1))
        .createResourceGroup(eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), any());
    verify(resourceGroupService, times(1)).upsert(any());
    verify(roleAssignmentService, times(2)).create(any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testEnablePublicAccess_enablingSecondResourceInAScope() {
    Scope scope = new Scope();
    scope.account(ACCOUNT_IDENTIFIER);
    scope.org(ORG_IDENTIFIER);
    scope.project(PROJECT_IDENTIFIER);
    ResourceGroupResponse resourceGroupResponse = buildResourceGroupResponse();
    ResourceType resourceType =
        ResourceType.builder().identifier(RESOURCE_TYPE).isPublic(true).permissionKey(RESOURCE_TYPE).build();
    mockStatic(NGRestUtils.class);
    Call<ResponseDTO<ResourceGroupResponse>> request = mock(Call.class);
    when(resourceGroupClient.getResourceGroup(
             PUBLIC_RESOURCE_GROUP_IDENTIFIER, scope.getAccount(), scope.getOrg(), scope.getProject()))
        .thenReturn(request);

    when(resourceGroupClient.updateResourceGroup(eq(PUBLIC_RESOURCE_GROUP_IDENTIFIER), eq(scope.getAccount()),
             eq(scope.getOrg()), eq(scope.getProject()), any()))
        .thenReturn(request);
    when(NGRestUtils.getResponse(request)).thenReturn(resourceGroupResponse);

    boolean result = publicAccessService.enable(RESOURCE_ID, resourceType, scope);
    verify(resourceGroupClient, times(1))
        .updateResourceGroup(eq(PUBLIC_RESOURCE_GROUP_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER),
            eq(PROJECT_IDENTIFIER), any());
    verify(resourceGroupService, times(0)).upsert(any());
    verify(roleAssignmentService, times(0)).create(any());
  }

  private ResourceGroupResponse buildResourceGroupResponse() {
    List<String> identifiers = new ArrayList<>();
    identifiers.add("testpipeline");
    List<ResourceSelector> resourceSelectors = new ArrayList<>();
    resourceSelectors.add(ResourceSelector.builder().resourceType("PIPELINE").identifiers(identifiers).build());
    return ResourceGroupResponse.builder()
        .resourceGroup(ResourceGroupDTO.builder()
                           .identifier(PUBLIC_RESOURCE_GROUP_IDENTIFIER)
                           .name(PUBLIC_RESOURCE_GROUP_NAME)
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .projectIdentifier(PROJECT_IDENTIFIER)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .allowedScopeLevels(Set.of("project"))
                           .resourceFilter(
                               ResourceFilter.builder().includeAllResources(false).resources(resourceSelectors).build())
                           .build())
        .build();
  }
}
