/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.domain.Pageable.unpaged;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.core.spring.OrganizationRepository;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.helpers.OrganizationInstrumentationHelper;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Collections;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class OrganizationServiceImplTest extends CategoryTest {
  @Mock private OrganizationRepository organizationRepository;
  @Mock private OutboxService outboxService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private NgUserService ngUserService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ScopeAccessHelper scopeAccessHelper;
  @Mock private OrganizationInstrumentationHelper instrumentationHelper;
  private OrganizationServiceImpl organizationService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    organizationService = spy(new OrganizationServiceImpl(organizationRepository, outboxService, transactionTemplate,
        ngUserService, accessControlClient, scopeAccessHelper, instrumentationHelper, ngFeatureFlagHelperService));
    when(scopeAccessHelper.getPermittedScopes(any())).then(returnsFirstArg());
  }

  private OrganizationDTO createOrganizationDTO(String identifier) {
    return OrganizationDTO.builder().identifier(identifier).name(randomAlphabetic(10)).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(randomAlphabetic(10));
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal("user", "admin@harness.io", "user", accountIdentifier))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);
    GlobalContextManager.set(globalContext);

    when(organizationRepository.save(organization)).thenReturn(organization);
    when(outboxService.save(any())).thenReturn(OutboxEvent.builder().build());
    when(transactionTemplate.execute(any())).thenReturn(organization);

    Organization createdOrganization = organizationService.create(accountIdentifier, organizationDTO);

    verify(transactionTemplate, times(1)).execute(any());

    assertEquals(organization, createdOrganization);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateExistentOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);

    when(organizationRepository.save(any())).thenReturn(organization);
    when(organizationService.get(accountIdentifier, identifier)).thenReturn(Optional.of(organization));

    organizationService.update(accountIdentifier, identifier, organizationDTO);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateOrganization_IncorrectPayload() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    organizationDTO.setName("");
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);
    organization.setName(randomAlphabetic(10));
    when(organizationService.get(accountIdentifier, identifier)).thenReturn(Optional.of(organization));

    organizationService.update(accountIdentifier, identifier, organizationDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNonExistentOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);

    when(organizationService.get(accountIdentifier, identifier)).thenReturn(Optional.empty());

    Organization updatedOrganization = organizationService.update(accountIdentifier, identifier, organizationDTO);

    assertNull(updatedOrganization);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    String searchTerm = randomAlphabetic(5);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(organizationRepository.findAllOrgs(any(Criteria.class))).thenReturn(Collections.emptyList());
    when(organizationRepository.findAll(any(Criteria.class), any(Pageable.class), anyBoolean()))
        .thenReturn(getPage(emptyList(), 0));

    Page<Organization> organizationPage = organizationService.listPermittedOrgs(
        accountIdentifier, unpaged(), OrganizationFilterDTO.builder().searchTerm(searchTerm).build());

    verify(organizationRepository, times(1)).findAllOrgs(criteriaArgumentCaptor.capture());

    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document criteriaObject = criteria.getCriteriaObject();

    assertEquals(3, criteriaObject.size());
    assertEquals(accountIdentifier, criteriaObject.get(OrganizationKeys.accountIdentifier));
    assertTrue(criteriaObject.containsKey(OrganizationKeys.deleted));

    assertEquals(0, organizationPage.getTotalElements());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Long version = 0L;
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

    when(organizationRepository.delete(any(), any(), any())).thenReturn(organization);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    organizationService.delete(accountIdentifier, identifier, version);
    verify(organizationRepository, times(1)).delete(any(), argumentCaptor.capture(), any());
    assertEquals(identifier, argumentCaptor.getValue());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testHardDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Long version = 0L;
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

    when(organizationRepository.delete(any(), any(), any())).thenReturn(organization);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(organizationRepository.hardDelete(any(), any(), any())).thenReturn(true);

    organizationService.delete(accountIdentifier, identifier, version);
    verify(organizationRepository, times(1)).hardDelete(any(), argumentCaptor.capture(), any());
    assertEquals(identifier, argumentCaptor.getValue());
    verify(organizationRepository, times(1)).delete(any(), argumentCaptor.capture(), any());
    assertEquals(identifier, argumentCaptor.getValue());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(any());
  }
}
