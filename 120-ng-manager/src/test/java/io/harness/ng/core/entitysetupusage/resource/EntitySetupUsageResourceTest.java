/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.resource;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(DX)
public class EntitySetupUsageResourceTest extends CategoryTest {
  @InjectMocks EntitySetupUsageResource entitySetupUsageResource;
  @Mock EntitySetupUsageService entitySetupUsageService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void listTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    String searchTerm = "searchTerm";
    String referredEntityFQN = "referredEntityFQN";
    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(new ArrayList<>());
    when(entitySetupUsageService.listAllEntityUsage(
             eq(100), eq(100), eq(accountIdentifier), eq(referredEntityFQN), eq(EntityType.CONNECTORS), eq(searchTerm)))
        .thenReturn(entitySetupUsageDTOPage);
    entitySetupUsageResource.listAllEntityUsage(
        100, 100, accountIdentifier, referredEntityFQN, EntityType.CONNECTORS, searchTerm);
    Mockito.verify(entitySetupUsageService, times(1))
        .listAllEntityUsage(
            eq(100), eq(100), eq(accountIdentifier), eq(referredEntityFQN), eq(EntityType.CONNECTORS), eq(searchTerm));
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testListAllEntityUsageWithSupportForTwoFqnForASingleEntity() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    String searchTerm = "searchTerm";
    String referredEntityFQN = "referredEntityFQN";
    String referredEntityFQN2 = "referredEntityFQN2";
    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(new ArrayList<>());
    when(entitySetupUsageService.listAllEntityUsageWithSupportForTwoFqnForASingleEntity(eq(100), eq(100),
             eq(accountIdentifier), eq(referredEntityFQN), eq(referredEntityFQN2), eq(EntityType.TEMPLATE),
             eq(searchTerm)))
        .thenReturn(entitySetupUsageDTOPage);
    entitySetupUsageResource.listAllEntityUsageWith2Fqns(
        100, 100, accountIdentifier, referredEntityFQN, referredEntityFQN2, EntityType.TEMPLATE, searchTerm);
    Mockito.verify(entitySetupUsageService, times(1))
        .listAllEntityUsageWithSupportForTwoFqnForASingleEntity(eq(100), eq(100), eq(accountIdentifier),
            eq(referredEntityFQN), eq(referredEntityFQN2), eq(EntityType.TEMPLATE), eq(searchTerm));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void isEntityReferenced() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    String referredEntityFQN = "referredEntityFQN";
    entitySetupUsageResource.isEntityReferenced(accountIdentifier, referredEntityFQN, EntityType.CONNECTORS);
    Mockito.verify(entitySetupUsageService, times(1))
        .isEntityReferenced(eq(accountIdentifier), eq(referredEntityFQN), eq(EntityType.CONNECTORS));
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void isEntityReferencedV2() {
    String accountIdentifier = "accountIdentifier";
    String referredEntityFQN = "referredEntityFQN";
    String searchTerm = "searchTerm";
    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(new ArrayList<>());
    when(entitySetupUsageService.listAllEntityUsage(
             eq(100), eq(100), eq(accountIdentifier), eq(referredEntityFQN), eq(EntityType.CONNECTORS), eq(searchTerm)))
        .thenReturn(entitySetupUsageDTOPage);
    entitySetupUsageResource.listAllEntityUsageV2(
        100, 100, accountIdentifier, referredEntityFQN, EntityType.CONNECTORS, searchTerm);
    Mockito.verify(entitySetupUsageService, times(1))
        .listAllEntityUsage(
            eq(100), eq(100), eq(accountIdentifier), eq(referredEntityFQN), eq(EntityType.CONNECTORS), eq(searchTerm));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void saveTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    EntitySetupUsageDTO entitySetupUsageDTO =
        EntitySetupUsageDTO.builder().accountIdentifier(accountIdentifier).build();
    entitySetupUsageResource.save(entitySetupUsageDTO);
    Mockito.verify(entitySetupUsageService, times(1)).save(eq(entitySetupUsageDTO));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void deleteTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    String referredEntityFQN = "referredEntityFQN";
    String referredByEntityFQN = "referredByEntityFQN";
    entitySetupUsageResource.delete(
        accountIdentifier, referredEntityFQN, EntityType.CONNECTORS, referredEntityFQN, EntityType.SECRETS);
    Mockito.verify(entitySetupUsageService, times(1))
        .delete(eq(accountIdentifier), eq(referredEntityFQN), eq(EntityType.CONNECTORS), eq(referredEntityFQN),
            eq(EntityType.SECRETS));
  }
}
