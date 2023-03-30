/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.JOHANNES;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.ServiceAccountFilterDTO;
import io.harness.ng.serviceaccounts.dto.ServiceAccountAggregateDTO;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ng.serviceaccounts.service.ServiceAccountDTOMapper;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.repositories.ng.serviceaccounts.ServiceAccountRepository;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;

import io.dropwizard.jersey.validation.JerseyViolationException;
import io.fabric8.utils.Lists;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class ServiceAccountServiceImplTest extends NgManagerTestBase {
  private ServiceAccountService serviceAccountService;
  private ServiceAccountRepository serviceAccountRepository;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String identifier;
  private String name;
  private String description;
  private ServiceAccountDTO serviceAccountRequestDTO;
  private AccountOrgProjectValidator accountOrgProjectValidator;
  private AccessControlClient accessControlClient;
  private AccessControlAdminClient accessControlAdminClient;
  private ApiKeyService apiKeyService;
  private TransactionTemplate transactionTemplate;

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = "accountId";
    orgIdentifier = "orgId";
    projectIdentifier = "projectId";
    identifier = "serviceaccountId";
    name = generateUuid();
    description = generateUuid();
    serviceAccountRepository = mock(ServiceAccountRepository.class);
    serviceAccountService = new ServiceAccountServiceImpl();
    accountOrgProjectValidator = mock(AccountOrgProjectValidator.class);
    accessControlClient = mock(AccessControlClient.class);
    accessControlAdminClient = mock(AccessControlAdminClient.class);
    apiKeyService = mock(ApiKeyService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    serviceAccountRequestDTO = ServiceAccountDTO.builder()
                                   .identifier(identifier)
                                   .name(name)
                                   .email(name + "@harness.io")
                                   .description(description)
                                   .tags(new HashMap<>())
                                   .accountIdentifier(accountIdentifier)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .build();
    doReturn(true).when(accountOrgProjectValidator).isPresent(anyString(), anyString(), anyString());
    FieldUtils.writeField(serviceAccountService, "serviceAccountRepository", serviceAccountRepository, true);
    FieldUtils.writeField(serviceAccountService, "accountOrgProjectValidator", accountOrgProjectValidator, true);
    FieldUtils.writeField(serviceAccountService, "transactionTemplate", transactionTemplate, true);
    FieldUtils.writeField(serviceAccountService, "accessControlClient", accessControlClient, true);
    FieldUtils.writeField(serviceAccountService, "accessControlAdminClient", accessControlAdminClient, true);
    FieldUtils.writeField(serviceAccountService, "apiKeyService", apiKeyService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateServiceAccount_WithoutIdentifier() {
    doReturn(ServiceAccount.builder().build())
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    ServiceAccountDTO serviceAccountRequestDTO = ServiceAccountDTO.builder()
                                                     .identifier(null)
                                                     .name(name)
                                                     .email(name + "@harness.io")
                                                     .description(description)
                                                     .tags(new HashMap<>())
                                                     .accountIdentifier(accountIdentifier)
                                                     .orgIdentifier(orgIdentifier)
                                                     .projectIdentifier(projectIdentifier)
                                                     .build();

    assertThatThrownBy(()
                           -> serviceAccountService.createServiceAccount(
                               accountIdentifier, orgIdentifier, projectIdentifier, serviceAccountRequestDTO))
        .isInstanceOf(JerseyViolationException.class);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateServiceAccount_WithoutDescription() {
    doReturn(ServiceAccount.builder().build())
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    ServiceAccountDTO serviceAccountRequestDTO = ServiceAccountDTO.builder()
                                                     .identifier(identifier)
                                                     .name(name)
                                                     .email(name + "@harness.io")
                                                     .tags(new HashMap<>())
                                                     .accountIdentifier(accountIdentifier)
                                                     .orgIdentifier(orgIdentifier)
                                                     .projectIdentifier(projectIdentifier)
                                                     .build();
    when(transactionTemplate.execute(any())).thenReturn(serviceAccountRequestDTO);
    ServiceAccount serviceAccount = ServiceAccountDTOMapper.getServiceAccountFromDTO(serviceAccountRequestDTO);
    doReturn(serviceAccount).when(serviceAccountRepository).save(any());

    ServiceAccountDTO serviceAccountResponse = serviceAccountService.createServiceAccount(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceAccountRequestDTO);
    assertThat(serviceAccountResponse.getDescription()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateServiceAccount_noAccountExists() {
    doReturn(null)
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    assertThatThrownBy(()
                           -> serviceAccountService.updateServiceAccount(accountIdentifier, orgIdentifier,
                               projectIdentifier, identifier, serviceAccountRequestDTO))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateServiceAccount_updateEmail() {
    doReturn(ServiceAccount.builder()
                 .accountIdentifier(accountIdentifier)
                 .orgIdentifier(orgIdentifier)
                 .projectIdentifier(projectIdentifier)
                 .identifier(identifier)
                 .email("svc@service.harness.io")
                 .build())
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    assertThatThrownBy(()
                           -> serviceAccountService.updateServiceAccount(accountIdentifier, orgIdentifier,
                               projectIdentifier, identifier, serviceAccountRequestDTO))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void listServiceAccountDTO() {
    doReturn(Lists.newArrayList(ServiceAccount.builder()
                                    .name(name)
                                    .identifier(identifier)
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build()))
        .when(serviceAccountRepository)
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier);
    List<ServiceAccount> accounts = serviceAccountService.listServiceAccounts(
        accountIdentifier, orgIdentifier, projectIdentifier, Collections.emptyList());
    assertThat(accounts.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void listServiceAccountDTOWithIdentifiers() {
    doReturn(Lists.newArrayList(ServiceAccount.builder()
                                    .name(name)
                                    .identifier(identifier)
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build()))
        .when(serviceAccountRepository)
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifierIsIn(
            accountIdentifier, orgIdentifier, projectIdentifier, Collections.singletonList(identifier));
    List<ServiceAccount> accounts = serviceAccountService.listServiceAccounts(
        accountIdentifier, orgIdentifier, projectIdentifier, Collections.singletonList(identifier));
    assertThat(accounts.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void listAggregateServiceAccounts() throws IOException {
    doReturn(new PageImpl<>(Lists.newArrayList(ServiceAccount.builder()
                                                   .name(name)
                                                   .identifier(identifier)
                                                   .accountIdentifier(accountIdentifier)
                                                   .orgIdentifier(orgIdentifier)
                                                   .projectIdentifier(projectIdentifier)
                                                   .build())))
        .when(serviceAccountRepository)
        .findAll(any(), any());
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);

    ResponseDTO<RoleAssignmentAggregateResponseDTO> restResponse =
        ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                    .roles(Collections.emptyList())
                                    .roleAssignments(Collections.emptyList())
                                    .resourceGroups(Collections.emptyList())
                                    .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Collections.emptyMap());

    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(accountIdentifier, orgIdentifier, projectIdentifier,
            Collections.singletonList(identifier), PageRequest.ofSize(1), ServiceAccountFilterDTO.builder().build());

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountAggregateDTOPageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void listAggregateServiceAccountsWithPermitted() throws IOException {
    doReturn(new PageImpl<>(Lists.newArrayList(ServiceAccount.builder()
                                                   .name(name)
                                                   .identifier(identifier)
                                                   .accountIdentifier(accountIdentifier)
                                                   .orgIdentifier(orgIdentifier)
                                                   .projectIdentifier(projectIdentifier)
                                                   .build())))
        .when(serviceAccountRepository)
        .findAll(any(), any());
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    ResponseDTO<RoleAssignmentAggregateResponseDTO> restResponse =
        ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                    .roles(Collections.emptyList())
                                    .roleAssignments(Collections.emptyList())
                                    .resourceGroups(Collections.emptyList())
                                    .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Collections.emptyMap());
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(List.of(AccessControlDTO.builder()
                                           .resourceIdentifier(identifier)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(accountIdentifier)
                                                              .orgIdentifier(orgIdentifier)
                                                              .projectIdentifier(projectIdentifier)
                                                              .build())
                                           .permitted(true)
                                           .build()))
            .build();
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponseDTO);

    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(accountIdentifier, orgIdentifier, projectIdentifier,
            Collections.singletonList(identifier), PageRequest.ofSize(1),
            ServiceAccountFilterDTO.builder()
                .orgIdentifier(orgIdentifier)
                .projectIdentifier(projectIdentifier)
                .identifiers(Collections.singletonList(identifier))
                .build());

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isNotEmpty();
    assertThat(serviceAccountAggregateDTOPageResponse.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void listAggregateServiceAccountsWithNonePermitted() throws IOException {
    doReturn(new PageImpl<>(Lists.newArrayList(ServiceAccount.builder()
                                                   .name(name)
                                                   .identifier(identifier)
                                                   .accountIdentifier(accountIdentifier)
                                                   .orgIdentifier(orgIdentifier)
                                                   .projectIdentifier(projectIdentifier)
                                                   .build())))
        .when(serviceAccountRepository)
        .findAll(any(), any());
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    ResponseDTO<RoleAssignmentAggregateResponseDTO> restResponse =
        ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                    .roles(Collections.emptyList())
                                    .roleAssignments(Collections.emptyList())
                                    .resourceGroups(Collections.emptyList())
                                    .build());
    Response<ResponseDTO<RoleAssignmentAggregateResponseDTO>> response = Response.success(restResponse);
    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(response);
    when(accessControlAdminClient.getAggregatedFilteredRoleAssignments(any(), any(), any(), any()))
        .thenReturn(responseDTOCall);
    when(apiKeyService.getApiKeysPerParentIdentifier(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Collections.emptyMap());
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(List.of(AccessControlDTO.builder()
                                           .resourceIdentifier(identifier)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(accountIdentifier)
                                                              .orgIdentifier(orgIdentifier)
                                                              .projectIdentifier(projectIdentifier)
                                                              .build())
                                           .permitted(false)
                                           .build()))
            .build();
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponseDTO);

    PageResponse<ServiceAccountAggregateDTO> serviceAccountAggregateDTOPageResponse =
        serviceAccountService.listAggregateServiceAccounts(accountIdentifier, orgIdentifier, projectIdentifier,
            Collections.singletonList(identifier), PageRequest.ofSize(1), ServiceAccountFilterDTO.builder().build());

    assertThat(serviceAccountAggregateDTOPageResponse.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetServiceAccountDTO() {
    doReturn(ServiceAccount.builder()
                 .name(name)
                 .identifier(identifier)
                 .accountIdentifier(accountIdentifier)
                 .orgIdentifier(orgIdentifier)
                 .projectIdentifier(projectIdentifier)
                 .build())
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    ServiceAccountDTO account =
        serviceAccountService.getServiceAccountDTO(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertThat(account).isNotNull();
    assertThat(account.getName()).isEqualTo(name);
    assertThat(account.getProjectIdentifier()).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testGetServiceAccountDTOWithoutOrgAndProject() {
    doReturn(ServiceAccount.builder()
                 .name(name)
                 .identifier(identifier)
                 .accountIdentifier(accountIdentifier)
                 .orgIdentifier(orgIdentifier)
                 .projectIdentifier(projectIdentifier)
                 .build())
        .when(serviceAccountRepository)
        .findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    ServiceAccountDTO account = serviceAccountService.getServiceAccountDTO(accountIdentifier, identifier);
    assertThat(account).isNotNull();
    assertThat(account.getName()).isEqualTo(name);
    assertThat(account.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(account.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(account.getProjectIdentifier()).isEqualTo(projectIdentifier);
  }
}
