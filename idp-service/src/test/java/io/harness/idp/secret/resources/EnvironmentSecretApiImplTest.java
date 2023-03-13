/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.resources;

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
import io.harness.idp.secret.service.EnvironmentSecretService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;
import io.harness.spec.server.idp.v1.model.EnvironmentSecretRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentSecretResponse;

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
public class EnvironmentSecretApiImplTest extends CategoryTest {
  static final String ERROR_MESSAGE = "Failed to replace secret. Code: 401, message: Unauthorized";
  AutoCloseable openMocks;
  @Mock EnvironmentSecretService environmentSecretService;
  @InjectMocks EnvironmentSecretApiImpl environmentSecretApiImpl;
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
    EnvironmentSecretRequest envSecretRequest = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecretRequest.setSecret(envSecret);
    when(environmentSecretService.saveAndSyncK8sSecret(envSecret, TEST_ACCOUNT_IDENTIFIER)).thenReturn(envSecret);
    Response response = environmentSecretApiImpl.createEnvironmentSecret(envSecretRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertEquals(envSecret, ((EnvironmentSecretResponse) response.getEntity()).getSecret());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateEnvironmentSecretError() {
    EnvironmentSecretRequest envSecretRequest = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecretRequest.setSecret(envSecret);
    when(environmentSecretService.saveAndSyncK8sSecret(envSecret, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response = environmentSecretApiImpl.createEnvironmentSecret(envSecretRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateEnvironmentSecrets() {
    EnvironmentSecretRequest envSecretRequest1 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecretRequest1.setSecret(envSecret1);
    EnvironmentSecretRequest envSecretRequest2 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecretRequest2.setSecret(envSecret2);
    List<EnvironmentSecret> secrets = Arrays.asList(envSecret1, envSecret2);
    when(environmentSecretService.saveAndSyncK8sSecrets(secrets, TEST_ACCOUNT_IDENTIFIER)).thenReturn(secrets);
    Response response = environmentSecretApiImpl.createEnvironmentSecrets(
        Arrays.asList(envSecretRequest1, envSecretRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    List<EnvironmentSecretResponse> responseList = (List<EnvironmentSecretResponse>) response.getEntity();
    assertEquals(2, responseList.size());
    assertEquals(envSecret1, responseList.get(0).getSecret());
    assertEquals(envSecret2, responseList.get(1).getSecret());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateEnvironmentSecretsError() {
    EnvironmentSecretRequest envSecretRequest1 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecretRequest1.setSecret(envSecret1);
    EnvironmentSecretRequest envSecretRequest2 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecretRequest2.setSecret(envSecret2);
    List<EnvironmentSecret> secrets = Arrays.asList(envSecret1, envSecret2);
    when(environmentSecretService.saveAndSyncK8sSecrets(secrets, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response = environmentSecretApiImpl.createEnvironmentSecrets(
        Arrays.asList(envSecretRequest1, envSecretRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecret() {
    Response response =
        environmentSecretApiImpl.deleteEnvironmentSecret(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecretError() {
    doThrow(new InvalidRequestException(ERROR_MESSAGE, USER))
        .when(environmentSecretService)
        .delete(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    Response response =
        environmentSecretApiImpl.deleteEnvironmentSecret(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecrets() {
    Response response = environmentSecretApiImpl.deleteEnvironmentSecrets(
        Arrays.asList(TEST_SECRET_IDENTIFIER, TEST_SECRET_IDENTIFIER1), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentSecretsError() {
    List<String> secretIdentifiers = Arrays.asList(TEST_SECRET_IDENTIFIER, TEST_SECRET_IDENTIFIER1);
    doThrow(new InvalidRequestException(ERROR_MESSAGE, USER))
        .when(environmentSecretService)
        .deleteMulti(secretIdentifiers, TEST_ACCOUNT_IDENTIFIER);
    Response response = environmentSecretApiImpl.deleteEnvironmentSecrets(secretIdentifiers, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetEnvironmentSecret() {
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecret.secretIdentifier(TEST_SECRET_IDENTIFIER);
    when(environmentSecretService.findByIdAndAccountIdentifier(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(envSecret));
    Response response = environmentSecretApiImpl.getEnvironmentSecret(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(envSecret, ((EnvironmentSecretResponse) response.getEntity()).getSecret());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetEnvironmentSecretNotFound() {
    Response response = environmentSecretApiImpl.getEnvironmentSecret(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetEnvironmentSecrets() {
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecret1.secretIdentifier(TEST_SECRET_IDENTIFIER);
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecret2.secretIdentifier(TEST_SECRET_IDENTIFIER1);
    when(environmentSecretService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Arrays.asList(envSecret1, envSecret2));
    Response response = environmentSecretApiImpl.getEnvironmentSecrets(TEST_ACCOUNT_IDENTIFIER, 1, 10, null);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    List<EnvironmentSecretResponse> responseList = (List<EnvironmentSecretResponse>) response.getEntity();
    assertEquals(2, responseList.size());
    assertEquals(envSecret1, responseList.get(0).getSecret());
    assertEquals(envSecret2, responseList.get(1).getSecret());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncEnvironmentSecrets() {
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecret1.secretIdentifier(TEST_SECRET_IDENTIFIER);
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecret2.secretIdentifier(TEST_SECRET_IDENTIFIER1);
    when(environmentSecretService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Arrays.asList(envSecret1, envSecret2));
    Response response = environmentSecretApiImpl.syncEnvironmentSecrets(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncEnvironmentSecretsError() {
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecret1.secretIdentifier(TEST_SECRET_IDENTIFIER);
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecret2.secretIdentifier(TEST_SECRET_IDENTIFIER1);
    List<EnvironmentSecret> secrets = Arrays.asList(envSecret1, envSecret2);
    when(environmentSecretService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(secrets);
    doThrow(new InvalidRequestException(ERROR_MESSAGE, USER))
        .when(environmentSecretService)
        .syncK8sSecret(secrets, TEST_ACCOUNT_IDENTIFIER);
    Response response = environmentSecretApiImpl.syncEnvironmentSecrets(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecret() {
    EnvironmentSecretRequest envSecretRequest = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecretRequest.setSecret(envSecret);
    when(environmentSecretService.updateAndSyncK8sSecret(envSecret, TEST_ACCOUNT_IDENTIFIER)).thenReturn(envSecret);
    Response response = environmentSecretApiImpl.updateEnvironmentSecret(
        TEST_SECRET_IDENTIFIER, envSecretRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(envSecret, ((EnvironmentSecretResponse) response.getEntity()).getSecret());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecretError() {
    EnvironmentSecretRequest envSecretRequest = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecretRequest.setSecret(envSecret);
    when(environmentSecretService.updateAndSyncK8sSecret(envSecret, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response = environmentSecretApiImpl.updateEnvironmentSecret(
        TEST_SECRET_IDENTIFIER, envSecretRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecrets() {
    EnvironmentSecretRequest envSecretRequest1 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecretRequest1.setSecret(envSecret1);
    EnvironmentSecretRequest envSecretRequest2 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecretRequest2.setSecret(envSecret2);
    List<EnvironmentSecret> secrets = Arrays.asList(envSecret1, envSecret2);
    when(environmentSecretService.updateAndSyncK8sSecrets(secrets, TEST_ACCOUNT_IDENTIFIER)).thenReturn(secrets);
    Response response = environmentSecretApiImpl.updateEnvironmentSecrets(
        Arrays.asList(envSecretRequest1, envSecretRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    List<EnvironmentSecretResponse> responseList = (List<EnvironmentSecretResponse>) response.getEntity();
    assertEquals(2, responseList.size());
    assertEquals(envSecret1, responseList.get(0).getSecret());
    assertEquals(envSecret2, responseList.get(1).getSecret());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentSecretsError() {
    EnvironmentSecretRequest envSecretRequest1 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecretRequest1.setSecret(envSecret1);
    EnvironmentSecretRequest envSecretRequest2 = new EnvironmentSecretRequest();
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecretRequest2.setSecret(envSecret2);
    List<EnvironmentSecret> secrets = Arrays.asList(envSecret1, envSecret2);
    when(environmentSecretService.updateAndSyncK8sSecrets(secrets, TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE, USER));
    Response response = environmentSecretApiImpl.updateEnvironmentSecrets(
        Arrays.asList(envSecretRequest1, envSecretRequest2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
