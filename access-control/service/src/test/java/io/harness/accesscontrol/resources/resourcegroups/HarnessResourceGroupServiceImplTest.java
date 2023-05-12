/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class HarnessResourceGroupServiceImplTest extends AccessControlTestBase {
  private ResourceGroupClient resourceGroupClient;
  private ResourceGroupFactory resourceGroupFactory;
  private ResourceGroupService resourceGroupService;
  private HarnessResourceGroupServiceImpl harnessResourceGroupService;
  private String accountIdentifier;
  private String orgIdentifier;
  private String identifier;
  private Scope scope;

  @Before
  public void setup() {
    resourceGroupClient = mock(ResourceGroupClient.class, RETURNS_DEEP_STUBS);
    resourceGroupFactory = mock(ResourceGroupFactory.class);
    resourceGroupService = mock(ResourceGroupService.class);
    harnessResourceGroupService =
        spy(new HarnessResourceGroupServiceImpl(resourceGroupClient, resourceGroupFactory, resourceGroupService));

    accountIdentifier = randomAlphabetic(10);
    orgIdentifier = randomAlphabetic(10);
    identifier = randomAlphabetic(10);
    scope = Scope.builder()
                .parentScope(Scope.builder()
                                 .parentScope(null)
                                 .instanceId(accountIdentifier)
                                 .level(HarnessScopeLevel.ACCOUNT)
                                 .build())
                .level(HarnessScopeLevel.ORGANIZATION)
                .instanceId(orgIdentifier)
                .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSync() throws IOException {
    ResourceGroupResponse resourceGroupResponse =
        ResourceGroupResponse.builder().resourceGroup(ResourceGroupDTO.builder().build()).build();
    Call<ResponseDTO<ResourceGroupResponse>> request = mock(Call.class);

    when(resourceGroupClient.getResourceGroup(identifier, accountIdentifier, orgIdentifier, null)).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(resourceGroupResponse)));

    when(resourceGroupFactory.buildResourceGroup(resourceGroupResponse, scope.toString()))
        .thenReturn(ResourceGroup.builder().build());
    when(resourceGroupFactory.buildResourceGroup(resourceGroupResponse)).thenReturn(ResourceGroup.builder().build());
    when(resourceGroupService.upsert(ResourceGroup.builder().build())).thenReturn(ResourceGroup.builder().build());

    harnessResourceGroupService.sync(identifier, scope);

    verify(resourceGroupClient, times(1)).getResourceGroup(any(), any(), any(), any());
    verify(resourceGroupFactory, times(1)).buildResourceGroup(any());
    verify(resourceGroupService, times(1)).upsert(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncNullScope() throws IOException {
    ResourceGroupResponse resourceGroupResponse =
        ResourceGroupResponse.builder().resourceGroup(ResourceGroupDTO.builder().build()).build();
    Call<ResponseDTO<ResourceGroupResponse>> request = mock(Call.class);

    when(resourceGroupClient.getResourceGroup(identifier, null, null, null)).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(resourceGroupResponse)));

    when(resourceGroupFactory.buildResourceGroup(resourceGroupResponse, null))
        .thenReturn(ResourceGroup.builder().build());
    when(resourceGroupFactory.buildResourceGroup(resourceGroupResponse)).thenReturn(ResourceGroup.builder().build());
    when(resourceGroupService.upsert(ResourceGroup.builder().build())).thenReturn(ResourceGroup.builder().build());

    harnessResourceGroupService.sync(identifier, null);

    verify(resourceGroupClient, times(1)).getResourceGroup(any(), any(), any(), any());
    verify(resourceGroupFactory, times(1)).buildResourceGroup(any());
    verify(resourceGroupService, times(1)).upsert(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncNotFound() throws IOException {
    Call<ResponseDTO<ResourceGroupResponse>> request = mock(Call.class);

    when(resourceGroupClient.getResourceGroup(identifier, accountIdentifier, orgIdentifier, null)).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse()));

    doNothing().when(resourceGroupService).deleteIfPresent(identifier, scope.toString());

    harnessResourceGroupService.sync(identifier, scope);

    verify(resourceGroupClient, times(1)).getResourceGroup(any(), any(), any(), any());
    verify(resourceGroupService, times(1)).deleteIfPresent(any(), any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncNullScopeNotFound() throws IOException {
    Call<ResponseDTO<ResourceGroupResponse>> request = mock(Call.class);

    when(resourceGroupClient.getResourceGroup(identifier, null, null, null)).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse()));

    doNothing().when(resourceGroupService).deleteManagedIfPresent(identifier);

    harnessResourceGroupService.sync(identifier, null);

    verify(resourceGroupClient, times(1)).getResourceGroup(any(), any(), any(), any());
    verify(resourceGroupService, times(1)).deleteManagedIfPresent(any());
  }
}
