/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.resources;

import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;
import io.harness.spec.server.idp.v1.model.BackstagePermissionsRequest;
import io.harness.spec.server.idp.v1.model.BackstagePermissionsResponse;

import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstagePermissionsApiImplTest extends CategoryTest {
  AutoCloseable openMocks;
  static final String ERROR_MESSAGE = "Failed to replace permissions. Code: 401, message: Unauthorized";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_USERGROUP = "IDP-ADMIN";
  static final List<String> TEST_PERMISSIONS =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");
  @Mock BackstagePermissionsService backstagePermissionsService;
  @InjectMocks BackstagePermissionsApiImpl backstagePermissionsApiImpl;
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetBackstagePermissions() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    when(backstagePermissionsService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(backstagePermissions));
    Response response = backstagePermissionsApiImpl.getBackstagePermissions(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetBackstagePermissionsNotFound() {
    Response response = backstagePermissionsApiImpl.getBackstagePermissions(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCreateBackstagePermissions() {
    BackstagePermissionsRequest backstagePermissionsRequest = new BackstagePermissionsRequest();
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissionsRequest.setData(backstagePermissions);
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    when(backstagePermissionsService.createPermissions(backstagePermissions, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(backstagePermissions);
    Response response =
        backstagePermissionsApiImpl.createBackstagePermissions(backstagePermissionsRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertEquals(backstagePermissions, ((BackstagePermissionsResponse) response.getEntity()).getData());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCreateBackstagePermissionsError() {
    BackstagePermissionsRequest backstagePermissionsRequest = new BackstagePermissionsRequest();
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissionsRequest.setData(backstagePermissions);
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    when(backstagePermissionsService.updatePermissions(backstagePermissions, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response =
        backstagePermissionsApiImpl.updateBackstagePermissions(backstagePermissionsRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testUpdateBackstagePermissions() {
    BackstagePermissionsRequest backstagePermissionsRequest = new BackstagePermissionsRequest();
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissionsRequest.setData(backstagePermissions);
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    when(backstagePermissionsService.updatePermissions(backstagePermissions, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(backstagePermissions);
    Response response =
        backstagePermissionsApiImpl.updateBackstagePermissions(backstagePermissionsRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(backstagePermissions, ((BackstagePermissionsResponse) response.getEntity()).getData());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testUpdateBackstagePermissionsError() {
    BackstagePermissionsRequest backstagePermissionsRequest = new BackstagePermissionsRequest();
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissionsRequest.setData(backstagePermissions);
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    when(backstagePermissionsService.createPermissions(backstagePermissions, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response =
        backstagePermissionsApiImpl.createBackstagePermissions(backstagePermissionsRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
