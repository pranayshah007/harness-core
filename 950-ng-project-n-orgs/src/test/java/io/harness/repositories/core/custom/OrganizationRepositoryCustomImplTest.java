/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.core.custom;

import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class OrganizationRepositoryCustomImplTest extends CategoryTest {
  private MongoTemplate mongoTemplate;
  private OrganizationRepositoryCustomImpl organizationRepository;

  @Before
  public void setup() {
    mongoTemplate = mock(MongoTemplate.class);
    organizationRepository = new OrganizationRepositoryCustomImpl(mongoTemplate);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testFindAll() {
    Organization organization = Organization.builder()
                                    .accountIdentifier(randomAlphabetic(10))
                                    .identifier(randomAlphabetic(10))
                                    .name(randomAlphabetic(10))
                                    .build();
    Pageable pageable = Pageable.unpaged();

    when(mongoTemplate.find(any(Query.class), eq(Organization.class))).thenReturn(singletonList(organization));
    when(mongoTemplate.count(any(Query.class), eq(Organization.class))).thenReturn(1L);

    Page<Organization> organizations = organizationRepository.findAll(new Criteria(), pageable, false);

    assertEquals(pageable, organizations.getPageable());
    assertEquals(1, organizations.getContent().size());
    assertEquals(organization, organizations.getContent().get(0));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Long version = 0L;
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    when(mongoTemplate.findAndModify(any(), any(), eq(Organization.class))).thenReturn(null);

    Boolean deleted = organizationRepository.delete(accountIdentifier, identifier, version) != null;

    verify(mongoTemplate, times(1))
        .findAndModify(queryArgumentCaptor.capture(), updateArgumentCaptor.capture(), eq(Organization.class));
    Query query = queryArgumentCaptor.getValue();
    Update update = updateArgumentCaptor.getValue();
    assertFalse(deleted);
    assertEquals(1, update.getUpdateObject().size());
    assertEquals(4, query.getQueryObject().size());
    assertTrue(query.getQueryObject().containsKey(OrganizationKeys.accountIdentifier));
    assertEquals(accountIdentifier, query.getQueryObject().get(OrganizationKeys.accountIdentifier));
    assertTrue(query.getQueryObject().containsKey(OrganizationKeys.identifier));
    assertEquals(identifier, query.getQueryObject().get(OrganizationKeys.identifier));
    assertTrue(query.getQueryObject().containsKey(OrganizationKeys.deleted));
    assertTrue(query.getQueryObject().containsKey(OrganizationKeys.version));
    assertEquals(version, query.getQueryObject().get(OrganizationKeys.version));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testRestore() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    when(mongoTemplate.findAndModify(any(), any(), eq(Organization.class))).thenReturn(null);

    Organization restoredOrganization = organizationRepository.restore(accountIdentifier, identifier);
    boolean deleted = restoredOrganization != null;
    verify(mongoTemplate, times(1))
        .findAndModify(queryArgumentCaptor.capture(), updateArgumentCaptor.capture(), eq(Organization.class));
    Query query = queryArgumentCaptor.getValue();
    Update update = updateArgumentCaptor.getValue();
    assertFalse(deleted);
    assertEquals(1, update.getUpdateObject().size());
    assertEquals(3, query.getQueryObject().size());
    assertTrue(query.getQueryObject().containsKey(OrganizationKeys.accountIdentifier));
    assertEquals(accountIdentifier, query.getQueryObject().get(OrganizationKeys.accountIdentifier));
    assertTrue(query.getQueryObject().containsKey(OrganizationKeys.identifier));
    assertEquals(identifier, query.getQueryObject().get(OrganizationKeys.identifier));
    assertTrue(query.getQueryObject().containsKey(OrganizationKeys.deleted));
    assertEquals(Boolean.TRUE, query.getQueryObject().get(OrganizationKeys.deleted));
  }
}
