/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.VINICIUS;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlHttpClient;
import io.harness.accesscontrol.clients.NonPrivilegedAccessControlClientImpl;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PL)
public class NonPrivilegedAccessControlClientImplTest {
  private NonPrivilegedAccessControlClientImpl accessControlClient;
  private AccessControlHttpClient accessControlHttpClient;

  @Before
  public void setup() {
    accessControlHttpClient = mock(AccessControlHttpClient.class, RETURNS_DEEP_STUBS);
    accessControlClient = new NonPrivilegedAccessControlClientImpl(accessControlHttpClient);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkForAccessOrThrow_WithEmpty_AccessPermissionList_ShouldReturnEmptyAccessPermissionResponse() {
    List<PermissionCheckDTO> permissionCheckDTOList = emptyList();
    AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccessOrThrow(permissionCheckDTOList);
    assertEquals(accessCheckResponseDTO.getAccessControlList(), emptyList());
    assertNull(accessCheckResponseDTO.getPrincipal());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkForAccessOrThrow_ForSomeResourceHavingPermission_ShouldReturnAllAccessPermitted()
      throws IOException {
    List<PermissionCheckDTO> permissionCheckDTOList = getPermissionsList();
    ResponseDTO<AccessCheckResponseDTO> restResponse = ResponseDTO.newResponse(getAccessCheckResponse(true));
    Response<ResponseDTO<AccessCheckResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<AccessCheckResponseDTO>> responseDTOCall = mock(Call.class);
    when(accessControlHttpClient.checkForAccess(any())).thenReturn(responseDTOCall);
    when(responseDTOCall.execute()).thenReturn(response);
    AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccessOrThrow(permissionCheckDTOList);
    verify(accessControlHttpClient, times(2)).checkForAccess(any());
    assertEquals(permissionCheckDTOList.size(), accessCheckResponseDTO.getAccessControlList().size());
  }

  private List<PermissionCheckDTO> getPermissionsList() {
    List<PermissionCheckDTO> permissionCheckDTOList = new ArrayList<>();
    for (int i = 0; i < 2000; i++) {
      ResourceScope resourceScope = ResourceScope.builder().accountIdentifier(randomAlphabetic(10000)).build();
      PermissionCheckDTO permissionCheckDTO = PermissionCheckDTO.builder()
                                                  .resourceScope(resourceScope)
                                                  .permission("some_entity_view")
                                                  .resourceType("some-entity_type")
                                                  .resourceIdentifier(randomAlphabetic(10000))
                                                  .build();
      permissionCheckDTOList.add(permissionCheckDTO);
    }
    return permissionCheckDTOList;
  }

  @Test(expected = NGAccessDeniedException.class)
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void checkForAccessOrThrow_ForSomeResourceNotHavingPermission_ShouldThrowNGAccessDeniedException()
      throws IOException {
    List<PermissionCheckDTO> permissionCheckDTOList = getPermissionsList();
    ResponseDTO<AccessCheckResponseDTO> restResponse = ResponseDTO.newResponse(getAccessCheckResponse(false));
    Response<ResponseDTO<AccessCheckResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<AccessCheckResponseDTO>> responseDTOCall = mock(Call.class);
    when(accessControlHttpClient.checkForAccess(any())).thenReturn(responseDTOCall);
    when(responseDTOCall.execute()).thenReturn(response);
    accessControlClient.checkForAccessOrThrow(permissionCheckDTOList);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void checkForAccessOrThrow_ForSomeResourceNotHavingPermission_ShouldThrowExceptionWithPrincipalInformation()
      throws IOException {
    List<PermissionCheckDTO> permissionCheckDTOList = getPermissionsList();
    AccessCheckResponseDTO accessCheckResponseDTO = getAccessCheckResponse(false);
    accessCheckResponseDTO.setPrincipal(
        Principal.builder().principalIdentifier("some_principal").principalType(PrincipalType.USER).build());
    ResponseDTO<AccessCheckResponseDTO> restResponse = ResponseDTO.newResponse(accessCheckResponseDTO);
    Response<ResponseDTO<AccessCheckResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<AccessCheckResponseDTO>> responseDTOCall = mock(Call.class);
    when(accessControlHttpClient.checkForAccess(any())).thenReturn(responseDTOCall);
    when(responseDTOCall.execute()).thenReturn(response);
    assertThatThrownBy(() -> accessControlClient.checkForAccessOrThrow(permissionCheckDTOList))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage(String.format("Principal of type USER with identifier some_principal : Missing permission %s on %s",
            accessCheckResponseDTO.getAccessControlList().get(0).getPermission(),
            accessCheckResponseDTO.getAccessControlList().get(0).getResourceType()));
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void checkForAccessOrThrow_ForSomeResourceNotHavingPermission_ShouldThrowExceptionWithCustomMessage()
      throws IOException {
    List<PermissionCheckDTO> permissionCheckDTOList = getPermissionsList();
    AccessCheckResponseDTO accessCheckResponseDTO = getAccessCheckResponse(false);
    ResponseDTO<AccessCheckResponseDTO> restResponse = ResponseDTO.newResponse(accessCheckResponseDTO);
    Response<ResponseDTO<AccessCheckResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<AccessCheckResponseDTO>> responseDTOCall = mock(Call.class);
    when(accessControlHttpClient.checkForAccess(any())).thenReturn(responseDTOCall);
    when(responseDTOCall.execute()).thenReturn(response);
    String customExceptionMessage = "CustomExceptionMessage";
    assertThatThrownBy(() -> accessControlClient.checkForAccessOrThrow(permissionCheckDTOList, customExceptionMessage))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage(customExceptionMessage);
  }

  private AccessCheckResponseDTO getAccessCheckResponse(boolean permitted) {
    List<AccessControlDTO> accessControlList = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      AccessControlDTO accessControlDTO = AccessControlDTO.builder()
                                              .permission("some_entity_view")
                                              .resourceType("some-entity_type")
                                              .resourceIdentifier(randomAlphabetic(10000))
                                              .permitted(permitted)
                                              .build();
      accessControlList.add(accessControlDTO);
    }
    return AccessCheckResponseDTO.builder().accessControlList(accessControlList).build();
  }
}
