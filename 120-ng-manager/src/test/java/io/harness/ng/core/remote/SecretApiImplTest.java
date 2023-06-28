/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.BOOPESH;

import static java.util.Optional.of;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.impl.NGEncryptedDataServiceImpl;
import io.harness.ng.core.api.impl.SecretCrudServiceImpl;
import io.harness.ng.core.api.impl.SecretPermissionValidator;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.spec.server.ng.v1.model.Secret;
import io.harness.spec.server.ng.v1.model.SecretRequest;
import io.harness.spec.server.ng.v1.model.SecretResponse;
import io.harness.spec.server.ng.v1.model.SecretSpec;
import io.harness.spec.server.ng.v1.model.SecretTextSpec;
import io.harness.spec.server.ng.v1.model.SecretValidationResponse;

import java.util.Collections;
import java.util.List;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;

@OwnedBy(PL)
public class SecretApiImplTest extends CategoryTest {
  private SecretCrudService ngSecretService;
  private NGEncryptedDataService ngEncryptedDataService;

  private AccountSecretApiImpl accountSecretApi;
  private OrgSecretApiImpl orgSecretApi;
  private ProjectSecretApiImpl projectSecretApi;

  private String account = "account";
  private String org = "organization";
  private String project = "project";
  private Boolean privateSecret = false;
  private String identifier = "secret_identifier";
  private String name = "secret_name";
  private String secretManagerIdentifier = "secretManagerIdentifier";
  private String secretValue = "secret_value";
  private Integer page = 0;
  private Integer limit = 50;
  private Validator validator;
  private SecretApiUtils secretApiUtils;

  private PageRequest pageRequest;

  @Before
  public void setup() {
    ngSecretService = mock(SecretCrudServiceImpl.class);
    ngEncryptedDataService = mock(NGEncryptedDataServiceImpl.class);
    pageRequest = PageRequest.builder()
                      .pageIndex(page)
                      .pageSize(limit)
                      .sortOrders(List.of(
                          SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
                      .build();
    doNothing().when(mock(SecretPermissionValidator.class)).checkForAccessOrThrow(any(), any(), any(), any());
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    secretApiUtils = new SecretApiUtils(validator);
    accountSecretApi = new AccountSecretApiImpl(
        ngSecretService, mock(SecretPermissionValidator.class), secretApiUtils, ngEncryptedDataService);
    orgSecretApi = new OrgSecretApiImpl(
        ngSecretService, mock(SecretPermissionValidator.class), secretApiUtils, ngEncryptedDataService);
    projectSecretApi = new ProjectSecretApiImpl(
        ngSecretService, mock(SecretPermissionValidator.class), secretApiUtils, ngEncryptedDataService);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateAccountScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(null, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.create(any(), any())).thenReturn(secretResponseWrapper);

    Response response = accountSecretApi.createAccountScopedSecret(secretRequest, account, privateSecret);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getOrg()).isNull();
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateAccountScopedSecretInvalidRequestException() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(org, project));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();
    when(ngSecretService.create(any(), any())).thenReturn(secretResponseWrapper);

    accountSecretApi.createAccountScopedSecret(secretRequest, account, privateSecret);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateOrgScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(org, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();
    when(ngSecretService.create(any(), any())).thenReturn(secretResponseWrapper);

    Response response = orgSecretApi.createOrgScopedSecret(secretRequest, org, account, privateSecret);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateOrgScopedSecretInvalidRequestException() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(null, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();
    when(ngSecretService.create(any(), any())).thenReturn(secretResponseWrapper);

    orgSecretApi.createOrgScopedSecret(secretRequest, org, account, privateSecret);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateProjectScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(org, project));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();
    when(ngSecretService.create(any(), any())).thenReturn(secretResponseWrapper);

    Response response = projectSecretApi.createProjectScopedSecret(secretRequest, org, project, account, privateSecret);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getProject()).isEqualTo(project);
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateProjectScopedSecretInvalidRequestException() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(null, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();
    when(ngSecretService.create(any(), any())).thenReturn(secretResponseWrapper);

    projectSecretApi.createProjectScopedSecret(secretRequest, org, project, account, privateSecret);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAccountScopedSecret() {
    Secret textSecret = getTextSecret(null, null);
    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(textSecret);
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.get(account, null, null, identifier)).thenReturn(of(secretResponseWrapper));

    Response response = accountSecretApi.getAccountScopedSecret(identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getOrg()).isNull();
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAccountScopedSecretNotFoundException() {
    accountSecretApi.getAccountScopedSecret(identifier, account);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetOrgScopedSecret() {
    Secret textSecret = getTextSecret(org, null);
    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(textSecret);
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.get(account, org, null, identifier)).thenReturn(of(secretResponseWrapper));

    Response response = orgSecretApi.getOrgScopedSecret(org, identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetOrgScopedSecretNotFoundException() {
    orgSecretApi.getOrgScopedSecret(org, identifier, account);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetProjectScopedSecret() {
    Secret textSecret = getTextSecret(org, project);
    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(textSecret);
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.get(account, org, project, identifier)).thenReturn(of(secretResponseWrapper));

    Response response = projectSecretApi.getProjectScopedSecret(org, project, identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getProject()).isEqualTo(project);
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAccountScopedSecretList() {
    Secret textSecret = getTextSecret(org, project);
    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(textSecret);
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder().secret(secretDTOV2).createdAt(123456789L).updatedAt(123456789L).build();
    Page<SecretResponseWrapper> pages = new PageImpl<>(Collections.singletonList(secretResponseWrapper));

    List<String> identifiers = Collections.singletonList(identifier);
    List<SecretType> secretTypes = secretApiUtils.toSecretTypes(Collections.singletonList("SSHKeyPath"));
    List<String> types = Collections.singletonList("SSHKeyPath");

    when(ngSecretService.list(anyString(), any(), any(), any(), any(), anyBoolean(), any(), any(), anyBoolean(), any()))
        .thenReturn(pages);

    Response response = accountSecretApi.getAccountScopedSecrets(
        identifiers, types, false, null, page, limit, account, "modified", Sort.Direction.DESC.toString());

    List<SecretResponse> secretResponse = (List<SecretResponse>) response.getEntity();
    assertThat(secretResponse.size()).isEqualTo(1);
    assertThat(secretResponse.get(0).getSecret().getProject()).isEqualTo(project);
    assertThat(secretResponse.get(0).getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.get(0).getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.get(0).getSecret().getName()).isEqualTo(name);
    assertThat(secretResponse.get(0).getCreated()).isNotNull();
    assertThat(secretResponse.get(0).getUpdated()).isNotNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetOrgScopedSecretList() {
    Secret textSecret = getTextSecret(org, project);
    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(textSecret);
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder().secret(secretDTOV2).createdAt(123456789L).updatedAt(123456789L).build();
    Page<SecretResponseWrapper> pages = new PageImpl<>(Collections.singletonList(secretResponseWrapper));

    List<String> identifiers = Collections.singletonList(identifier);
    List<SecretType> secretTypes = secretApiUtils.toSecretTypes(Collections.singletonList("SSHKeyPath"));
    List<String> types = Collections.singletonList("SSHKeyPath");

    when(ngSecretService.list(
             anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), any(), anyBoolean(), any()))
        .thenReturn(pages);

    Response response = orgSecretApi.getOrgScopedSecrets(
        org, identifiers, types, false, null, page, limit, account, "modified", Sort.Direction.DESC.toString());

    List<SecretResponse> secretResponse = (List<SecretResponse>) response.getEntity();
    assertThat(secretResponse.size()).isEqualTo(1);
    assertThat(secretResponse.get(0).getSecret().getProject()).isEqualTo(project);
    assertThat(secretResponse.get(0).getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.get(0).getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.get(0).getSecret().getName()).isEqualTo(name);
    assertThat(secretResponse.get(0).getCreated()).isNotNull();
    assertThat(secretResponse.get(0).getUpdated()).isNotNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetProjectScopedSecretList() {
    Secret textSecret = getTextSecret(org, project);
    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(textSecret);
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder().secret(secretDTOV2).createdAt(123456789L).updatedAt(123456789L).build();
    Page<SecretResponseWrapper> pages = new PageImpl<>(Collections.singletonList(secretResponseWrapper));

    List<String> identifiers = Collections.singletonList(identifier);
    List<SecretType> secretTypes = secretApiUtils.toSecretTypes(Collections.singletonList("SSHKeyPath"));
    List<String> types = Collections.singletonList("SSHKeyPath");

    when(ngSecretService.list(
             anyString(), anyString(), any(), any(), any(), anyBoolean(), any(), any(), anyBoolean(), any()))
        .thenReturn(pages);

    Response response = projectSecretApi.getProjectScopedSecrets(org, project, identifiers, types, false, null, page,
        limit, account, "modified", Sort.Direction.DESC.toString());

    List<SecretResponse> secretResponse = (List<SecretResponse>) response.getEntity();
    assertThat(secretResponse.size()).isEqualTo(1);
    assertThat(secretResponse.get(0).getSecret().getProject()).isEqualTo(project);
    assertThat(secretResponse.get(0).getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.get(0).getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.get(0).getSecret().getName()).isEqualTo(name);
    assertThat(secretResponse.get(0).getCreated()).isNotNull();
    assertThat(secretResponse.get(0).getUpdated()).isNotNull();
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetProjectScopedSecretNotFoundException() {
    projectSecretApi.getProjectScopedSecret(org, project, identifier, account);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateAccountScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(null, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.update(any(), any(), any(), any(), any())).thenReturn(secretResponseWrapper);

    Response response = accountSecretApi.updateAccountScopedSecret(secretRequest, identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getOrg()).isNull();
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateOrgScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(org, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.update(any(), any(), any(), any(), any())).thenReturn(secretResponseWrapper);

    Response response = orgSecretApi.updateOrgScopedSecret(secretRequest, org, identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateProjectScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(org, project));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.update(any(), any(), any(), any(), any())).thenReturn(secretResponseWrapper);

    Response response = projectSecretApi.updateProjectScopedSecret(secretRequest, org, project, identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getProject()).isEqualTo(project);
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteProjectScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(org, project));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.get(any(), any(), any(), any())).thenReturn(of(secretResponseWrapper));
    when(ngSecretService.delete(any(), any(), any(), any(), eq(false))).thenReturn(true);

    Response response = projectSecretApi.deleteProjectScopedSecret(org, project, identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getProject()).isEqualTo(project);
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteOrgScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(org, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.get(any(), any(), any(), any())).thenReturn(of(secretResponseWrapper));
    when(ngSecretService.delete(any(), any(), any(), any(), eq(false))).thenReturn(true);

    Response response = orgSecretApi.deleteOrgScopedSecret(org, identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getOrg()).isEqualTo(org);
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteAccountScopedSecret() {
    SecretRequest secretRequest = new SecretRequest();
    secretRequest.setSecret(getTextSecret(null, null));

    SecretDTOV2 secretDTOV2 = secretApiUtils.toSecretDto(secretRequest.getSecret());
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().secret(secretDTOV2).build();

    when(ngSecretService.get(any(), any(), any(), any())).thenReturn(of(secretResponseWrapper));
    when(ngSecretService.delete(any(), any(), any(), any(), eq(false))).thenReturn(true);

    Response response = accountSecretApi.deleteAccountScopedSecret(identifier, account);

    SecretResponse secretResponse = (SecretResponse) response.getEntity();
    assertThat(secretResponse.getSecret().getOrg()).isNull();
    assertThat(secretResponse.getSecret().getProject()).isNull();
    assertThat(secretResponse.getSecret().getIdentifier()).isEqualTo(identifier);
    assertThat(secretResponse.getSecret().getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidateProjectSecretRef_Success() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String secretRefPath = randomAlphabetic(10);
    SecretRequest secretRequest =
        new SecretRequest().secret(new Secret()
                                       .name(randomAlphabetic(10))
                                       .identifier(randomAlphabetic(10))
                                       .spec(new SecretTextSpec()
                                                 .value(secretRefPath)
                                                 .valueType(SecretTextSpec.ValueTypeEnum.REFERENCE)
                                                 .secretManagerIdentifier(secretManagerIdentifier)
                                                 .type(SecretSpec.TypeEnum.SECRETTEXT)));
    when(ngEncryptedDataService.validateSecretRef(anyString(), anyString(), anyString(), any())).thenReturn(true);
    Response response = projectSecretApi.validateProjectSecretRef(org, project, secretRequest, account);
    SecretValidationResponse secretValidationResponse = (SecretValidationResponse) response.getEntity();
    assertThat(secretValidationResponse.isSuccess());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidateProjectSecretRef_Negative() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String secretRefPath = randomAlphabetic(10);
    SecretRequest secretRequest =
        new SecretRequest().secret(new Secret()
                                       .name(randomAlphabetic(10))
                                       .identifier(randomAlphabetic(10))
                                       .spec(new SecretTextSpec()
                                                 .value(secretRefPath)
                                                 .valueType(SecretTextSpec.ValueTypeEnum.REFERENCE)
                                                 .secretManagerIdentifier(secretManagerIdentifier)
                                                 .type(SecretSpec.TypeEnum.SECRETTEXT)));
    when(ngEncryptedDataService.validateSecretRef(anyString(), anyString(), anyString(), any())).thenReturn(false);
    Response response = projectSecretApi.validateProjectSecretRef(org, project, secretRequest, account);
    SecretValidationResponse secretValidationResponse = (SecretValidationResponse) response.getEntity();
    assertThat(!secretValidationResponse.isSuccess());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidateOrgSecretRef_Success() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String secretRefPath = randomAlphabetic(10);
    SecretRequest secretRequest =
        new SecretRequest().secret(new Secret()
                                       .name(randomAlphabetic(10))
                                       .identifier(randomAlphabetic(10))
                                       .spec(new SecretTextSpec()
                                                 .value(secretRefPath)
                                                 .valueType(SecretTextSpec.ValueTypeEnum.REFERENCE)
                                                 .secretManagerIdentifier(secretManagerIdentifier)
                                                 .type(SecretSpec.TypeEnum.SECRETTEXT)));
    when(ngEncryptedDataService.validateSecretRef(anyString(), anyString(), any(), any())).thenReturn(true);
    Response response = orgSecretApi.validateOrgSecretRef(org, secretRequest, account);
    SecretValidationResponse secretValidationResponse = (SecretValidationResponse) response.getEntity();
    assertThat(secretValidationResponse.isSuccess());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidateAccountSecretRef_Success() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String secretRefPath = randomAlphabetic(10);
    SecretRequest secretRequest =
        new SecretRequest().secret(new Secret()
                                       .name(randomAlphabetic(10))
                                       .identifier(randomAlphabetic(10))
                                       .spec(new SecretTextSpec()
                                                 .value(secretRefPath)
                                                 .valueType(SecretTextSpec.ValueTypeEnum.REFERENCE)
                                                 .secretManagerIdentifier(secretManagerIdentifier)
                                                 .type(SecretSpec.TypeEnum.SECRETTEXT)));
    when(ngEncryptedDataService.validateSecretRef(anyString(), any(), any(), any())).thenReturn(true);
    Response response = accountSecretApi.validateAccountSecretRef(secretRequest, account);
    SecretValidationResponse secretValidationResponse = (SecretValidationResponse) response.getEntity();
    assertThat(secretValidationResponse.isSuccess());
  }

  private Secret getTextSecret(String org, String project) {
    Secret secret = new Secret();
    secret.setIdentifier(identifier);
    secret.setName(name);
    secret.setOrg(org);
    secret.setProject(project);

    SecretTextSpec secretTextSpec = new SecretTextSpec();
    secretTextSpec.setType(SecretSpec.TypeEnum.SECRETTEXT);
    secretTextSpec.secretManagerIdentifier(secretManagerIdentifier);
    secretTextSpec.setValue(secretValue);
    secretTextSpec.setValueType(SecretTextSpec.ValueTypeEnum.INLINE);
    secret.setSpec(secretTextSpec);
    return secret;
  }
}
