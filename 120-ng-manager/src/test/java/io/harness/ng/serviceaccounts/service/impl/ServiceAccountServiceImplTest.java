/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.repositories.ng.serviceaccounts.ServiceAccountRepository;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

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
    doReturn(Arrays.asList(ServiceAccount.builder()
                               .name(name)
                               .identifier(identifier)
                               .accountIdentifier(accountIdentifier)
                               .orgIdentifier(orgIdentifier)
                               .projectIdentifier(projectIdentifier)
                               .build()))
        .when(serviceAccountRepository)
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier);
    List<ServiceAccountDTO> accounts = serviceAccountService.listServiceAccounts(
        accountIdentifier, orgIdentifier, projectIdentifier, Collections.emptyList());
    assertThat(accounts.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void listServiceAccountDTOWithIdentifiers() {
    doReturn(Arrays.asList(ServiceAccount.builder()
                               .name(name)
                               .identifier(identifier)
                               .accountIdentifier(accountIdentifier)
                               .orgIdentifier(orgIdentifier)
                               .projectIdentifier(projectIdentifier)
                               .build()))
        .when(serviceAccountRepository)
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifierIsIn(
            accountIdentifier, orgIdentifier, projectIdentifier, Collections.singletonList(identifier));
    List<ServiceAccountDTO> accounts = serviceAccountService.listServiceAccounts(
        accountIdentifier, orgIdentifier, projectIdentifier, Collections.singletonList(identifier));
    assertThat(accounts.size()).isEqualTo(1);
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
}
