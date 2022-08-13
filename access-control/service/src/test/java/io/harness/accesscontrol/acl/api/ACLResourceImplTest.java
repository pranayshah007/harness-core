/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.api;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.acl.ACLService;
import io.harness.accesscontrol.acl.ResourceAttributeProvider;
import io.harness.accesscontrol.preference.services.AccessControlPreferenceService;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.PL)
public class ACLResourceImplTest extends AccessControlTestBase {
  private ACLResourceImpl aclResource;

  @Before
  public void setup() {
    ACLService aclService = mock(ACLService.class);
    AccessControlPreferenceService accessControlPreferenceService = mock(AccessControlPreferenceService.class);
    PrivilegedRoleAssignmentService privilegedRoleAssignmentService = mock(PrivilegedRoleAssignmentService.class);
    ResourceAttributeProvider resourceAttributeProvider = mock(ResourceAttributeProvider.class);
    aclResource = new ACLResourceImpl(
        aclService, accessControlPreferenceService, privilegedRoleAssignmentService, resourceAttributeProvider);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testAccessCheckWithNullPermissionCheckDTO() {
    Principal principal = Principal.builder().build();
    AccessCheckRequestDTO accessCheckRequestDTO =
        AccessCheckRequestDTO.builder().principal(Principal.builder().build()).permissions(null).build();
    ResponseDTO<AccessCheckResponseDTO> accessCheckResponse = aclResource.get(accessCheckRequestDTO);
    assertThat(principal).isEqualTo(accessCheckResponse.getData().getPrincipal());
    assertThat(accessCheckResponse.getData().getAccessControlList()).isNotNull();
    assertThat(accessCheckResponse.getData().getAccessControlList()).isEmpty();
  }

  @Test()
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testAccessCheck_WithNoPrincipalInContext_ThrowsInvalidRequestException() {
    Map<String, String> map = new HashMap<String,String>();
    map.put("testKey",  "testValue");
    PermissionCheckDTO permissionCheckDTO = PermissionCheckDTO.builder().resourceAttributes(map)
            .resourceIdentifier("testIdentifier").build();
AccessCheckRequestDTO accessCheckRequestDTO = AccessCheckRequestDTO.builder().
        permissions(Collections.singletonList(permissionCheckDTO)).build();
    try {
      aclResource.get(accessCheckRequestDTO);
      fail("InvalidRequestException not thrown");
    }
    catch (InvalidRequestException exception) {
      assertThat(exception.getMessage()).isEqualTo("Missing principal in context or User doesn't have permission to check access for a different principal");
    }
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testAccessCheck_WithBothResourceAttributesAndIdentifier_ThrowsInvalidRequestException() {
// Following as recommended here:
// https://github.com/junit-team/junit4/wiki/Exception-testing
    
  }
}
