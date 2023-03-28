/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.resources;

import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.*;

import java.util.Arrays;
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
public class BackstageEnvVariableApiImplTest extends CategoryTest {
  static final String ERROR_MESSAGE = "Failed to replace secret. Code: 401, message: Unauthorized";
  AutoCloseable openMocks;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @InjectMocks BackstageEnvVariableApiImpl backstageEnvVariableApiImpl;
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_SECRET_IDENTIFIER = "secretId";
  static final String TEST_SECRET_IDENTIFIER1 = "secretId1";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateEnvironmentSecret() {
    BackstageEnvVariableRequest envVariableRequest = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable = new BackstageEnvVariable();
    envVariableRequest.setEnvVariable(envVariable);
    when(backstageEnvVariableService.create(envVariable, TEST_ACCOUNT_IDENTIFIER)).thenReturn(envVariable);
    Response response =
        backstageEnvVariableApiImpl.createBackstageEnvVariable(envVariableRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertEquals(envVariable, ((BackstageEnvVariableResponse) response.getEntity()).getEnvVariable());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateEnvironmentSecretError() {
    BackstageEnvVariableRequest envVariableRequest = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable = new BackstageEnvVariable();
    envVariableRequest.setEnvVariable(envVariable);
    when(backstageEnvVariableService.create(envVariable, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response =
        backstageEnvVariableApiImpl.createBackstageEnvVariable(envVariableRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateEnvironmentSecrets() {
    BackstageEnvVariableRequest envVariableRequest1 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable1 = new BackstageEnvVariable();
    envVariableRequest1.setEnvVariable(envVariable1);
    BackstageEnvVariableRequest envVariableRequest2 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable2 = new BackstageEnvVariable();
    envVariableRequest2.setEnvVariable(envVariable2);
    List<BackstageEnvVariable> secrets = Arrays.asList(envVariable1, envVariable2);
    when(backstageEnvVariableService.createMulti(secrets, TEST_ACCOUNT_IDENTIFIER)).thenReturn(secrets);
    Response response = backstageEnvVariableApiImpl.createBackstageEnvVariables(
        Arrays.asList(envVariableRequest1, envVariableRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    List<BackstageEnvVariableResponse> responseList = (List<BackstageEnvVariableResponse>) response.getEntity();
    assertEquals(2, responseList.size());
    assertEquals(envVariable1, responseList.get(0).getEnvVariable());
    assertEquals(envVariable2, responseList.get(1).getEnvVariable());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateEnvironmentSecretsError() {
    BackstageEnvVariableRequest envVariableRequest1 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable1 = new BackstageEnvVariable();
    envVariableRequest1.setEnvVariable(envVariable1);
    BackstageEnvVariableRequest envVariableRequest2 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable2 = new BackstageEnvVariable();
    envVariableRequest2.setEnvVariable(envVariable2);
    List<BackstageEnvVariable> secrets = Arrays.asList(envVariable1, envVariable2);
    when(backstageEnvVariableService.createMulti(secrets, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response = backstageEnvVariableApiImpl.createBackstageEnvVariables(
        Arrays.asList(envVariableRequest1, envVariableRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecret() {
    Response response =
        backstageEnvVariableApiImpl.deleteBackstageEnvVariable(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecretError() {
    doThrow(new InvalidRequestException(ERROR_MESSAGE, USER))
        .when(backstageEnvVariableService)
        .delete(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    Response response =
        backstageEnvVariableApiImpl.deleteBackstageEnvVariable(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecrets() {
    Response response = backstageEnvVariableApiImpl.deleteBackstageEnvVariables(
        Arrays.asList(TEST_SECRET_IDENTIFIER, TEST_SECRET_IDENTIFIER1), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecretsError() {
    List<String> secretIdentifiers = Arrays.asList(TEST_SECRET_IDENTIFIER, TEST_SECRET_IDENTIFIER1);
    doThrow(new InvalidRequestException(ERROR_MESSAGE, USER))
        .when(backstageEnvVariableService)
        .deleteMulti(secretIdentifiers, TEST_ACCOUNT_IDENTIFIER);
    Response response =
        backstageEnvVariableApiImpl.deleteBackstageEnvVariables(secretIdentifiers, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetEnvironmentSecret() {
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    when(backstageEnvVariableService.findByIdAndAccountIdentifier(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(envVariable));
    Response response =
        backstageEnvVariableApiImpl.getBackstageEnvVariable(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(envVariable, ((BackstageEnvVariableResponse) response.getEntity()).getEnvVariable());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetEnvironmentSecretNotFound() {
    Response response =
        backstageEnvVariableApiImpl.getBackstageEnvVariable(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetEnvironmentSecrets() {
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    when(backstageEnvVariableService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Arrays.asList(envVariable1, envVariable2));
    Response response = backstageEnvVariableApiImpl.getBackstageEnvVariables(TEST_ACCOUNT_IDENTIFIER, 1, 10, null);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    List<BackstageEnvVariableResponse> responseList = (List<BackstageEnvVariableResponse>) response.getEntity();
    assertEquals(2, responseList.size());
    assertEquals(envVariable1, responseList.get(0).getEnvVariable());
    assertEquals(envVariable2, responseList.get(1).getEnvVariable());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncEnvironmentSecrets() {
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    when(backstageEnvVariableService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Arrays.asList(envVariable1, envVariable2));
    Response response = backstageEnvVariableApiImpl.syncBackstageEnvVariables(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncEnvironmentSecretsError() {
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    List<BackstageEnvVariable> secrets = Arrays.asList(envVariable1, envVariable2);
    when(backstageEnvVariableService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(secrets);
    doThrow(new InvalidRequestException(ERROR_MESSAGE, USER))
        .when(backstageEnvVariableService)
        .sync(secrets, TEST_ACCOUNT_IDENTIFIER);
    Response response = backstageEnvVariableApiImpl.syncBackstageEnvVariables(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecret() {
    BackstageEnvVariableRequest envVariableRequest = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable = new BackstageEnvVariable();
    envVariableRequest.setEnvVariable(envVariable);
    when(backstageEnvVariableService.update(envVariable, TEST_ACCOUNT_IDENTIFIER)).thenReturn(envVariable);
    Response response = backstageEnvVariableApiImpl.updateBackstageEnvVariable(
        TEST_SECRET_IDENTIFIER, envVariableRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(envVariable, ((BackstageEnvVariableResponse) response.getEntity()).getEnvVariable());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecretError() {
    BackstageEnvVariableRequest envVariableRequest = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable = new BackstageEnvVariable();
    envVariableRequest.setEnvVariable(envVariable);
    when(backstageEnvVariableService.update(envVariable, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response = backstageEnvVariableApiImpl.updateBackstageEnvVariable(
        TEST_SECRET_IDENTIFIER, envVariableRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecrets() {
    BackstageEnvVariableRequest envVariableRequest1 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable1 = new BackstageEnvVariable();
    envVariableRequest1.setEnvVariable(envVariable1);
    BackstageEnvVariableRequest envVariableRequest2 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable2 = new BackstageEnvVariable();
    envVariableRequest2.setEnvVariable(envVariable2);
    List<BackstageEnvVariable> secrets = Arrays.asList(envVariable1, envVariable2);
    when(backstageEnvVariableService.updateMulti(secrets, TEST_ACCOUNT_IDENTIFIER)).thenReturn(secrets);
    Response response = backstageEnvVariableApiImpl.updateBackstageEnvVariables(
        Arrays.asList(envVariableRequest1, envVariableRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    List<BackstageEnvVariableResponse> responseList = (List<BackstageEnvVariableResponse>) response.getEntity();
    assertEquals(2, responseList.size());
    assertEquals(envVariable1, responseList.get(0).getEnvVariable());
    assertEquals(envVariable2, responseList.get(1).getEnvVariable());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecretsError() {
    BackstageEnvVariableRequest envVariableRequest1 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable1 = new BackstageEnvVariable();
    envVariableRequest1.setEnvVariable(envVariable1);
    BackstageEnvVariableRequest envVariableRequest2 = new BackstageEnvVariableRequest();
    BackstageEnvVariable envVariable2 = new BackstageEnvVariable();
    envVariableRequest2.setEnvVariable(envVariable2);
    List<BackstageEnvVariable> secrets = Arrays.asList(envVariable1, envVariable2);
    when(backstageEnvVariableService.updateMulti(secrets, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response = backstageEnvVariableApiImpl.updateBackstageEnvVariables(
        Arrays.asList(envVariableRequest1, envVariableRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
