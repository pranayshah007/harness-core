/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.rule.OwnerRule.JOHANNES;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.agent.beans.AgentMtlsEndpoint;
import io.harness.agent.beans.AgentMtlsEndpoint.AgentMtlsEndpointKeys;
import io.harness.agent.beans.AgentMtlsEndpointDetails;
import io.harness.agent.beans.AgentMtlsEndpointRequest;
import io.harness.agent.beans.AgentMtlsMode;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.impl.AgentMtlsEndpointServiceImpl;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AgentMtlsEndpointServiceImplTest extends WingsBaseTest {
  // Common values used for uts
  private static final String SUBDOMAIN = "agent.ut.harness.io";
  private static final String CA_CERTIFICATES = "--Cert-in-PEM-format--";
  private static final String CA_CERTIFICATES_2 = "--Cert2-in-PEM-format--";
  private static final String DOMAIN_PREFIX = "customer1";
  private static final String DOMAIN_PREFIX_2 = "customer2";

  private final Random random = new Random();
  private AgentMtlsEndpointServiceImpl service;

  @Inject private HPersistence persistence;

  @Before
  public void initialize() {
    this.service = new AgentMtlsEndpointServiceImpl(this.persistence, SUBDOMAIN);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testCreateEndpointForAccountCreatesCorrectEntry() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();
    AgentMtlsEndpointRequest request = AgentMtlsEndpointRequest.builder()
                                           .domainPrefix(DOMAIN_PREFIX)
                                           .caCertificates(CA_CERTIFICATES)
                                           .mode(AgentMtlsMode.STRICT)
                                           .build();

    AgentMtlsEndpointDetails returnedDetails = this.service.createEndpointForAccount(accountId, request);

    // ensure returned entry is correct
    assertEquals(true, StringUtils.isNotEmpty(returnedDetails.getUuid()));
    assertEquals(DOMAIN_PREFIX + "." + SUBDOMAIN, returnedDetails.getFqdn());
    assertEquals(accountId, returnedDetails.getAccountId());
    assertEquals(CA_CERTIFICATES, returnedDetails.getCaCertificates());
    assertEquals(AgentMtlsMode.STRICT, returnedDetails.getMode());

    // ensure stored entry is correct
    AgentMtlsEndpointDetails storedDetails = this.service.getEndpointForAccount(accountId);
    assertEquals(returnedDetails.getUuid(), storedDetails.getUuid());
    assertEquals(returnedDetails.getFqdn(), storedDetails.getFqdn());
    assertEquals(returnedDetails.getAccountId(), storedDetails.getAccountId());
    assertEquals(returnedDetails.getCaCertificates(), storedDetails.getCaCertificates());
    assertEquals(returnedDetails.getMode(), storedDetails.getMode());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateEndpointForAccountUpdatesEntryCorrect() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    AgentMtlsEndpointDetails createDetails = this.service.createEndpointForAccount(accountId, createRequest);

    // update entry
    AgentMtlsEndpointRequest updateRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX_2)
                                                 .caCertificates(CA_CERTIFICATES_2)
                                                 .mode(AgentMtlsMode.LOOSE)
                                                 .build();

    AgentMtlsEndpointDetails updateDetails = this.service.updateEndpointForAccount(accountId, updateRequest);

    // ensure returned entry is correct
    assertEquals(true, StringUtils.isNotEmpty(updateDetails.getUuid()));
    assertEquals(createDetails.getUuid(), updateDetails.getUuid());
    assertEquals(accountId, updateDetails.getAccountId());
    assertEquals(updateRequest.getDomainPrefix() + "." + SUBDOMAIN, updateDetails.getFqdn());
    assertEquals(updateRequest.getCaCertificates(), updateDetails.getCaCertificates());
    assertEquals(updateRequest.getMode(), updateDetails.getMode());

    // ensure stored entry is correct
    AgentMtlsEndpointDetails storedDetails = this.service.getEndpointForAccount(accountId);
    assertEquals(updateDetails.getUuid(), storedDetails.getUuid());
    assertEquals(updateDetails.getFqdn(), storedDetails.getFqdn());
    assertEquals(updateDetails.getAccountId(), storedDetails.getAccountId());
    assertEquals(updateDetails.getCaCertificates(), storedDetails.getCaCertificates());
    assertEquals(updateDetails.getMode(), storedDetails.getMode());
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateEndpointForAccountThrowsIfEndpointDoesntExist() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // update entry
    AgentMtlsEndpointRequest updateRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.LOOSE)
                                                 .build();

    this.service.updateEndpointForAccount(accountId, updateRequest);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testPatchEndpointForAccountUpdatesEntryCorrect() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    AgentMtlsEndpointDetails createDetails = this.service.createEndpointForAccount(accountId, createRequest);

    // update entry
    AgentMtlsEndpointRequest updateRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX_2)
                                                 .caCertificates(CA_CERTIFICATES_2)
                                                 .mode(AgentMtlsMode.LOOSE)
                                                 .build();

    AgentMtlsEndpointDetails updateDetails = this.service.patchEndpointForAccount(accountId, updateRequest);

    // ensure returned entry is correct
    assertEquals(true, StringUtils.isNotEmpty(updateDetails.getUuid()));
    assertEquals(createDetails.getUuid(), updateDetails.getUuid());
    assertEquals(accountId, updateDetails.getAccountId());
    assertEquals(updateRequest.getDomainPrefix() + "." + SUBDOMAIN, updateDetails.getFqdn());
    assertEquals(updateRequest.getCaCertificates(), updateDetails.getCaCertificates());
    assertEquals(updateRequest.getMode(), updateDetails.getMode());

    // ensure stored entry is correct
    AgentMtlsEndpointDetails storedDetails = this.service.getEndpointForAccount(accountId);
    assertEquals(updateDetails.getUuid(), storedDetails.getUuid());
    assertEquals(updateDetails.getFqdn(), storedDetails.getFqdn());
    assertEquals(updateDetails.getAccountId(), storedDetails.getAccountId());
    assertEquals(updateDetails.getCaCertificates(), storedDetails.getCaCertificates());
    assertEquals(updateDetails.getMode(), storedDetails.getMode());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testPatchEndpointForAccountForOnlyDomainPrefix() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    AgentMtlsEndpointDetails createDetails = this.service.createEndpointForAccount(accountId, createRequest);

    // patch only domain prefix
    AgentMtlsEndpointRequest patchRequest = AgentMtlsEndpointRequest.builder().domainPrefix(DOMAIN_PREFIX_2).build();

    AgentMtlsEndpointDetails patchDetails = this.service.patchEndpointForAccount(accountId, patchRequest);

    // ensure stored entry is correct
    assertEquals(createDetails.getUuid(), patchDetails.getUuid());
    assertEquals(patchRequest.getDomainPrefix() + "." + SUBDOMAIN, patchDetails.getFqdn());
    assertEquals(createDetails.getAccountId(), patchDetails.getAccountId());
    assertEquals(createDetails.getCaCertificates(), patchDetails.getCaCertificates());
    assertEquals(createDetails.getMode(), patchDetails.getMode());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testPatchEndpointForAccountForOnlyMode() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    AgentMtlsEndpointDetails createDetails = this.service.createEndpointForAccount(accountId, createRequest);

    // patch only mode
    AgentMtlsEndpointRequest patchRequest = AgentMtlsEndpointRequest.builder().mode(AgentMtlsMode.LOOSE).build();

    AgentMtlsEndpointDetails patchDetails = this.service.patchEndpointForAccount(accountId, patchRequest);

    // ensure stored entry is correct
    assertEquals(createDetails.getUuid(), patchDetails.getUuid());
    assertEquals(createDetails.getFqdn(), patchDetails.getFqdn());
    assertEquals(createDetails.getAccountId(), patchDetails.getAccountId());
    assertEquals(createDetails.getCaCertificates(), patchDetails.getCaCertificates());
    assertEquals(patchRequest.getMode(), patchDetails.getMode());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testPatchEndpointForAccountForOnlyCaCertificates() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    AgentMtlsEndpointDetails createDetails = this.service.createEndpointForAccount(accountId, createRequest);

    // patch only caCertificates
    AgentMtlsEndpointRequest patchRequest =
        AgentMtlsEndpointRequest.builder().caCertificates(CA_CERTIFICATES_2).build();

    AgentMtlsEndpointDetails patchDetails = this.service.patchEndpointForAccount(accountId, patchRequest);

    // ensure stored entry is correct
    assertEquals(createDetails.getUuid(), patchDetails.getUuid());
    assertEquals(createDetails.getFqdn(), patchDetails.getFqdn());
    assertEquals(createDetails.getAccountId(), patchDetails.getAccountId());
    assertEquals(patchRequest.getCaCertificates(), patchDetails.getCaCertificates());
    assertEquals(createDetails.getMode(), patchDetails.getMode());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testPatchEndpointForAccountFailsWithoutRequestProperty() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);

    // patch nothing
    AgentMtlsEndpointRequest emptyPatchRequest = AgentMtlsEndpointRequest.builder().build();
    this.service.patchEndpointForAccount(accountId, emptyPatchRequest);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testPatchEndpointForAccountThrowsIfEndpointDoesntExist() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // update entry
    AgentMtlsEndpointRequest updateRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.LOOSE)
                                                 .build();

    this.service.patchEndpointForAccount(accountId, updateRequest);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testDeleteEndpointForAccountRemovesEntry() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);

    AgentMtlsEndpoint endpoint =
        persistence.createQuery(AgentMtlsEndpoint.class).field(AgentMtlsEndpointKeys.accountId).equal(accountId).get();

    assertNotNull("Endpoint should exist as we just created it.", endpoint);

    Boolean deleted = this.service.deleteEndpointForAccount(accountId);
    assertTrue("Endpoint should've been deleted.", deleted);

    endpoint =
        persistence.createQuery(AgentMtlsEndpoint.class).field(AgentMtlsEndpointKeys.accountId).equal(accountId).get();

    assertNull("Endpoint just got deleted.", endpoint);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testGetEndpointForAccountReturnsCorrectEntry() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry first
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);
    AgentMtlsEndpointDetails endpoint = this.service.getEndpointForAccount(accountId);

    // ensure returned entry is correct
    assertEquals(true, StringUtils.isNotEmpty(endpoint.getUuid()));
    assertEquals(accountId, endpoint.getAccountId());
    assertEquals(createRequest.getDomainPrefix() + "." + SUBDOMAIN, endpoint.getFqdn());
    assertEquals(createRequest.getCaCertificates(), endpoint.getCaCertificates());
    assertEquals(createRequest.getMode(), endpoint.getMode());
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testGetEndpointForAccountThrowsIfEntryDoesntExist() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    this.service.getEndpointForAccount(accountId);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testIsDomainPrefixAvailable() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();
    String domainPrefix1 = this.getRandomDomainPrefix();
    String domainPrefix2 = this.getRandomDomainPrefix();

    // should be available before creation
    assertTrue(this.service.isDomainPrefixAvailable(domainPrefix1));
    assertTrue(this.service.isDomainPrefixAvailable(domainPrefix2));

    // create entry
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(domainPrefix1)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);
    this.service.getEndpointForAccount(accountId);

    // created one shouldn't be available anymore
    assertFalse(this.service.isDomainPrefixAvailable(domainPrefix1));
    assertTrue(this.service.isDomainPrefixAvailable(domainPrefix2));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testValidationCaCertificateNull() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(null)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testValidationModeNull() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(DOMAIN_PREFIX)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(null)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testDomainPrefixNull() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix(null)
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testDomainPrefixContainsMultipleLevel() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix("a.b")
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testDomainPrefixLeadsToInvalidFqdn() {
    // Use unique accountId to avoid overlap with other tests.
    String accountId = UUIDGenerator.generateUuid();

    // create entry
    AgentMtlsEndpointRequest createRequest = AgentMtlsEndpointRequest.builder()
                                                 .domainPrefix("someInvalid%Character")
                                                 .caCertificates(CA_CERTIFICATES)
                                                 .mode(AgentMtlsMode.STRICT)
                                                 .build();
    this.service.createEndpointForAccount(accountId, createRequest);
  }

  private String getRandomDomainPrefix() {
    return String.format("customer-%d", this.random.nextLong());
  }
}
