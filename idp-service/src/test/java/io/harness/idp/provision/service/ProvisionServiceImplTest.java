/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.provision.service;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.client.NgConnectorManagerClient;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ProvisionServiceImplTest {
  @InjectMocks private ProvisionServiceImpl provisionServiceImpl;
  @Mock NgConnectorManagerClient ngConnectorManagerClient;
  @Mock BackstagePermissionsService backstagePermissionsService;
  private static final String ADMIN_USER_ID = "lv0euRhKRCyiXWzS7pOg6g";
  private static final String DEFAULT_USER_ID = "0osgWsTZRsSZ8RWfjLRkEg";
  private static final String ACCOUNT_ID = "123";
  static final String TEST_USERGROUP = " ";
  static final List<String> TEST_PERMISSIONS =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckUserAuthorization() {
    MockedStatic<SecurityContextBuilder> mockSecurityContext = Mockito.mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", ACCOUNT_ID));
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);
    provisionServiceImpl.checkUserAuthorization();
    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(ADMIN_USER_ID);
    mockSecurityContext.close();
    mockRestUtils.close();
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = SARTHAK_KASAT)
  public void testCreateDefaultBackstagePermissions() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    provisionServiceImpl.createDefaultPermissions(ACCOUNT_ID);
    verify(backstagePermissionsService).createPermissions(backstagePermissions, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckUserAuthorizationThrowsException() {
    MockedStatic<SecurityContextBuilder> mockSecurityContext = Mockito.mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(DEFAULT_USER_ID, "default@harness.io", "default", ACCOUNT_ID));
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    try {
      provisionServiceImpl.checkUserAuthorization();
    } catch (Exception e) {
      String expectedMessage = String.format("User : %s not allowed to provision IDP", DEFAULT_USER_ID);
      Assert.assertEquals(expectedMessage, e.getMessage());
    }
    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(DEFAULT_USER_ID);
    mockSecurityContext.close();
    mockRestUtils.close();
  }
}
