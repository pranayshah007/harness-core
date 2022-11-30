package io.harness;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.clients.AccessControlHttpClient;
import io.harness.accesscontrol.clients.NonPrivilegedAccessControlClientImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class NonPrivilegedAccessControlClientImplTest {
  private NonPrivilegedAccessControlClientImpl accessControlClient;
  private AccessControlHttpClient accessControlHttpClient;

  @Before
  public void setup() {
    accessControlHttpClient = mock(AccessControlHttpClient.class);
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
}
