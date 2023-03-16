/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.impl;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.EntityType.PIPELINES;
import static io.harness.EntityType.SECRETS;
import static io.harness.annotations.dev.HarnessTeam.DX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.EntitySetupUsageTestBase;
import io.harness.ng.core.entitysetupusage.dto.EntityReferencesDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.springframework.data.domain.Page;

@OwnedBy(DX)
public class EntitySetupUsageServiceImplTest extends EntitySetupUsageTestBase {
  @Inject @InjectMocks EntitySetupUsageService entitySetupUsageService;
  String accountIdentifier = "accountIdentifier";
  String orgIdentifier = "orgIdentifier";
  String projectIdentifier = "projectIdentifier";
  String referredIdentifier = "referredIdentifier1";
  String referredIdentifier1 = "referredIdentifier11";
  String referredIdentifier2 = "referredIdentifier12";
  String referredByIdentifier = "referredByIdentifier1";
  String referredEntityName = "Connector 1";
  String referredByEntityName = "Pipeline 1";

  private EntitySetupUsageDTO createEntityReference(
      String accountIdentifier, EntityDetail referredEntity, EntityDetail referredByEntity) {
    return EntitySetupUsageDTO.builder()
        .accountIdentifier(accountIdentifier)
        .referredEntity(referredEntity)
        .referredByEntity(referredByEntity)
        .build();
  }

  private EntityDetail getEntityDetails(String identifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String name, EntityType type) {
    EntityReference referredByEntityRef =
        IdentifierRefHelper.getIdentifierRef(identifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(referredByEntityRef).name(name).type(type).build();
  }

  private EntityDetail getEntityDetailsWithRepoBranch(String identifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String name, EntityType type, String repo, String branch, Boolean isDefault) {
    IdentifierRef ref = IdentifierRef.builder()
                            .scope(io.harness.encryption.Scope.PROJECT)
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .identifier(identifier)
                            .repoIdentifier(repo)
                            .branch(branch)
                            .isDefault(isDefault)
                            .build();
    return EntityDetail.builder().entityRef(ref).name(name).type(type).build();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void isEntityReferenced() {
    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    saveEntitySetupUsage();
    boolean entityReferenceExists =
        entitySetupUsageService.isEntityReferenced(accountIdentifier, referredEntityFQN, CONNECTORS);
    assertThat(entityReferenceExists).isTrue();

    boolean doesEntityReferenceExists =
        entitySetupUsageService.isEntityReferenced(accountIdentifier, "identifierWhichIsNotReferenced", CONNECTORS);
    assertThat(doesEntityReferenceExists).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void isEntityReferencedForBranch() {
    String repo = "repo";
    String branch = "branch";
    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    createSetupUsageRecordsWithBranches(new ArrayList<>(), repo, branch, false, false, 1, referredIdentifier);
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch(branch).yamlGitConfigId(repo).build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      boolean entityReferenceExists =
          entitySetupUsageService.isEntityReferenced(accountIdentifier, referredEntityFQN, CONNECTORS);
      assertThat(entityReferenceExists).isTrue();
    }

    final GitEntityInfo newContext = GitEntityInfo.builder().branch("branch1").yamlGitConfigId(repo).build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newContext).build());
      boolean entityReferenceExists =
          entitySetupUsageService.isEntityReferenced(accountIdentifier, referredEntityFQN, CONNECTORS);
      assertThat(entityReferenceExists).isFalse();
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void isEntityReferencedForDefault() {
    String repo = "repo";
    String branch = "branch";
    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    createSetupUsageRecordsWithBranches(new ArrayList<>(), repo, branch, true, false, 1, referredIdentifier);
    boolean entityReferenceExists =
        entitySetupUsageService.isEntityReferenced(accountIdentifier, referredEntityFQN, CONNECTORS);
    assertThat(entityReferenceExists).isTrue();

    String newReferredIdentifier = "newReferredIdentifier";
    String newReferredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, newReferredIdentifier);
    createSetupUsageRecordsWithBranches(new ArrayList<>(), repo, branch, false, false, 1, newReferredIdentifier);
    boolean doesEntityReferenceExists =
        entitySetupUsageService.isEntityReferenced(accountIdentifier, newReferredEntityFQN, CONNECTORS);
    assertThat(doesEntityReferenceExists).isFalse();
  }

  private void createSetupUsageRecords(
      List<EntitySetupUsageDTO> setupUsages, int numberOfRecords, String referredIdentifier) {
    String referredEntityName = "Connector";
    for (int i = 0; i < numberOfRecords; i++) {
      String referredByIdentifier = "referredByIdentifier" + i;
      String referredByEntityName = "Pipeline" + i;
      EntityDetail referredByEntity = getEntityDetails(
          referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES);
      EntityDetail referredEntity = getEntityDetails(
          referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
      EntitySetupUsageDTO entitySetupUsageDTO =
          createEntityReference(accountIdentifier, referredEntity, referredByEntity);
      setupUsages.add(entitySetupUsageDTO);
      entitySetupUsageService.save(entitySetupUsageDTO);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void listTest() {
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();
    String referredIdentifier = "referredIdentifier";

    createSetupUsageRecords(setupUsages, 3, referredIdentifier);

    // Adding one extra setup usage for different entity
    createSetupUsageRecords(setupUsages, 2, referredIdentifier + "1");

    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    Page<EntitySetupUsageDTO> entityReferenceDTOPage =
        entitySetupUsageService.listAllEntityUsage(0, 10, accountIdentifier, referredEntityFQN, CONNECTORS, null);
    assertThat(entityReferenceDTOPage.getTotalElements()).isEqualTo(3);
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(0), setupUsages.get(0));
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(1), setupUsages.get(1));
  }

  private void createSetupUsageRecordsGivenRefferedBy(
      List<EntitySetupUsageDTO> setupUsages, int numberOfRecords, String referredByIdentifier) {
    for (int i = 0; i < numberOfRecords; i++) {
      String referredIdentifier = "referredIdentifier" + i;
      String referredEntityName = "Connector" + i;
      EntityDetail referredByEntity = getEntityDetails(
          referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES);
      EntityDetail referredEntity = getEntityDetails(
          referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
      EntitySetupUsageDTO entitySetupUsageDTO =
          createEntityReference(accountIdentifier, referredEntity, referredByEntity);
      setupUsages.add(entitySetupUsageDTO);
      entitySetupUsageService.save(entitySetupUsageDTO);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListAllReferredUsages() {
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();

    createSetupUsageRecordsGivenRefferedBy(setupUsages, 5, referredByIdentifier);

    // Adding one extra setup usage for different entity
    createSetupUsageRecordsGivenRefferedBy(setupUsages, 2, referredByIdentifier + "1");

    String referredByEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredByIdentifier);
    List<EntitySetupUsageDTO> entityReferenceDTOPage =
        entitySetupUsageService.listAllReferredUsages(0, 10, accountIdentifier, referredByEntityFQN, CONNECTORS, null);
    assertThat(entityReferenceDTOPage.size()).isEqualTo(5);
    verifyTheValuesAreCorrect(entityReferenceDTOPage.get(0), setupUsages.get(0));
    verifyTheValuesAreCorrect(entityReferenceDTOPage.get(1), setupUsages.get(1));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void listTestWithSearchTerm() {
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();
    String referredIdentifier = "referredIdentifier";

    createSetupUsageRecords(setupUsages, 3, referredIdentifier);

    // Adding one extra setup usage for different entity
    createSetupUsageRecords(setupUsages, 2, referredIdentifier + "1");

    // Create more records with different name, so that it doesn't comes in searchterm
    for (int i = 0; i < 3; i++) {
      String referredByIdentifier = "referredByIdentifier" + i;
      String referredByEntityName = "refferedByName" + i;
      EntityDetail referredByEntity = getEntityDetails(
          referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES);
      EntityDetail referredEntity = getEntityDetails(
          referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
      EntitySetupUsageDTO entitySetupUsageDTO =
          createEntityReference(accountIdentifier, referredEntity, referredByEntity);
      setupUsages.add(entitySetupUsageDTO);
      entitySetupUsageService.save(entitySetupUsageDTO);
    }

    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    Page<EntitySetupUsageDTO> entityReferenceDTOPage =
        entitySetupUsageService.listAllEntityUsage(0, 10, accountIdentifier, referredEntityFQN, CONNECTORS, "Pipeline");
    assertThat(entityReferenceDTOPage.getTotalElements()).isEqualTo(3);
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(0), setupUsages.get(0));
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(1), setupUsages.get(1));
  }

  private void createSetupUsageRecordsWithBranches(List<EntitySetupUsageDTO> setupUsages, String repo, String branch,
      Boolean isDefaultReferredEntity, Boolean isDefaultReferredByEntity, int numberOfRecords,
      String referredIdentifier) {
    String referredEntityName = "Connector";
    for (int i = 0; i < numberOfRecords; i++) {
      String referredByIdentifier = "referredByIdentifier" + i;
      String referredByEntityName = "Pipeline" + i;
      EntityDetail referredByEntity = getEntityDetailsWithRepoBranch(referredByIdentifier, accountIdentifier,
          orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES, repo, branch, isDefaultReferredByEntity);
      EntityDetail referredEntity = getEntityDetailsWithRepoBranch(referredIdentifier, accountIdentifier, orgIdentifier,
          projectIdentifier, referredEntityName, CONNECTORS, repo, branch, isDefaultReferredEntity);
      EntitySetupUsageDTO entitySetupUsageDTO =
          createEntityReference(accountIdentifier, referredEntity, referredByEntity);
      setupUsages.add(entitySetupUsageDTO);
      entitySetupUsageService.save(entitySetupUsageDTO);
    }
  }

  private void createSetupUsageRecordsForReferredBy(List<EntitySetupUsageDTO> setupUsages, String repo, String branch,
      Boolean isDefaultReferredEntity, Boolean isDefaultReferredByEntity, int numberOfRecords,
      String referredByIdentifier) {
    for (int i = 0; i < numberOfRecords; i++) {
      String referredIdentifier = "referredByIdentifier" + i;
      String referredEntityName = "Pipeline" + i;
      EntityDetail referredByEntity = getEntityDetailsWithRepoBranch(referredByIdentifier, accountIdentifier,
          orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES, repo, branch, isDefaultReferredByEntity);
      EntityDetail referredEntity = getEntityDetailsWithRepoBranch(referredIdentifier, accountIdentifier, orgIdentifier,
          projectIdentifier, referredEntityName, CONNECTORS, repo, branch, isDefaultReferredEntity);
      EntitySetupUsageDTO entitySetupUsageDTO =
          createEntityReference(accountIdentifier, referredEntity, referredByEntity);
      setupUsages.add(entitySetupUsageDTO);
      entitySetupUsageService.save(entitySetupUsageDTO);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListEntityUsageWithBranch() {
    String repo = "repo";
    String branch = "branch";
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();
    String referredIdentifier = "referredIdentifier";

    // Adding records which belong to this repo and branch
    createSetupUsageRecordsWithBranches(setupUsages, repo, branch, false, false, 5, referredIdentifier);

    // Adding one extra setup usage for different entity
    createSetupUsageRecordsWithBranches(setupUsages, repo, branch, false, false, 5, referredIdentifier + "1");

    // Adding some extra setup usage for a different branch
    createSetupUsageRecordsWithBranches(setupUsages, repo, "branch1", false, false, 2, referredIdentifier);

    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch(branch).yamlGitConfigId(repo).build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      Page<EntitySetupUsageDTO> entityReferenceDTOPage =
          entitySetupUsageService.listAllEntityUsage(0, 10, accountIdentifier, referredEntityFQN, CONNECTORS, null);
      assertThat(entityReferenceDTOPage.getTotalElements()).isEqualTo(5);
      verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(0), setupUsages.get(0));
      verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(1), setupUsages.get(1));

      Page<EntitySetupUsageDTO> listAPIResponse = entitySetupUsageService.list(
          0, 10, accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier, CONNECTORS, null);
      assertThat(listAPIResponse.getTotalElements()).isEqualTo(5);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListAllReferredEntityWithBranch() {
    String repo = "repo";
    String branch = "branch";
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();

    // Adding records which belong to this repo and branch
    createSetupUsageRecordsForReferredBy(setupUsages, repo, branch, false, false, 5, referredByIdentifier);

    // Adding one extra setup usage for different entity
    createSetupUsageRecordsForReferredBy(setupUsages, repo, branch, false, false, 5, referredByIdentifier + "1");

    // Adding some extra setup usage for a different branch
    createSetupUsageRecordsForReferredBy(setupUsages, repo, "branch1", false, false, 2, referredByIdentifier);

    String referredByEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredByIdentifier);
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch(branch).yamlGitConfigId(repo).build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      List<EntitySetupUsageDTO> entityReferenceDTOPage = entitySetupUsageService.listAllReferredUsages(
          0, 10, accountIdentifier, referredByEntityFQN, CONNECTORS, null);
      assertThat(entityReferenceDTOPage.size()).isEqualTo(5);
      verifyTheValuesAreCorrect(entityReferenceDTOPage.get(0), setupUsages.get(0));
      verifyTheValuesAreCorrect(entityReferenceDTOPage.get(1), setupUsages.get(1));
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListEntityUsageForDefault() {
    String repo = "repo";
    String branch = "branch";
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();
    String referredIdentifier = "referredIdentifier";

    // Adding records which belong to this repo and branch
    createSetupUsageRecordsWithBranches(setupUsages, repo, branch, true, false, 5, referredIdentifier);

    // Adding one extra setup usage for different entity
    createSetupUsageRecordsWithBranches(setupUsages, repo, branch, true, false, 5, referredIdentifier + "1");

    // Adding some extra setup usage for entities which are not git syncable
    createSetupUsageRecords(setupUsages, 2, referredIdentifier);

    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    Page<EntitySetupUsageDTO> entityReferenceDTOPage =
        entitySetupUsageService.listAllEntityUsage(0, 10, accountIdentifier, referredEntityFQN, CONNECTORS, null);
    assertThat(entityReferenceDTOPage.getTotalElements()).isEqualTo(7);
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(0), setupUsages.get(0));
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(1), setupUsages.get(1));

    Page<EntitySetupUsageDTO> listAPIResponse = entitySetupUsageService.list(
        0, 10, accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier, CONNECTORS, null);
    assertThat(listAPIResponse.getTotalElements()).isEqualTo(7);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListAllReferredUsagesForDefault() {
    String repo = "repo";
    String branch = "branch";
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();

    // Adding records which belong to this repo and branch
    createSetupUsageRecordsWithBranches(setupUsages, repo, branch, true, false, 5, referredIdentifier);

    // Adding one extra setup usage for different entity
    createSetupUsageRecordsWithBranches(setupUsages, repo, branch, true, false, 5, referredIdentifier + "1");

    // Adding some extra setup usage for entities which are not git syncable
    createSetupUsageRecords(setupUsages, 2, referredIdentifier);

    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    Page<EntitySetupUsageDTO> entityReferenceDTOPage =
        entitySetupUsageService.listAllEntityUsage(0, 10, accountIdentifier, referredEntityFQN, CONNECTORS, null);
    assertThat(entityReferenceDTOPage.getTotalElements()).isEqualTo(7);
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(0), setupUsages.get(0));
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(1), setupUsages.get(1));

    Page<EntitySetupUsageDTO> listAPIResponse = entitySetupUsageService.list(
        0, 10, accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier, CONNECTORS, null);
    assertThat(listAPIResponse.getTotalElements()).isEqualTo(7);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListEntityUsageForDefaultWithSearchTerm() {
    String repo = "repo";
    String branch = "branch";
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();
    String referredIdentifier = "referredIdentifier";

    // Adding records which belong to this repo and branch
    createSetupUsageRecordsWithBranches(setupUsages, repo, branch, true, false, 5, referredIdentifier);

    // Adding some extra setup usage for entities which are not git syncable
    createSetupUsageRecords(setupUsages, 2, referredIdentifier);

    // Adding some extra records for different name, so that it is not covered in search term filter
    for (int i = 0; i < 3; i++) {
      String referredByIdentifier = "referredByIdentifier" + i;
      String referredByEntityName = "refferedByName" + i;
      EntityDetail referredByEntity = getEntityDetails(
          referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES);
      EntityDetail referredEntity = getEntityDetails(
          referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
      EntitySetupUsageDTO entitySetupUsageDTO =
          createEntityReference(accountIdentifier, referredEntity, referredByEntity);
      setupUsages.add(entitySetupUsageDTO);
      entitySetupUsageService.save(entitySetupUsageDTO);
    }

    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    Page<EntitySetupUsageDTO> entityReferenceDTOPage =
        entitySetupUsageService.listAllEntityUsage(0, 10, accountIdentifier, referredEntityFQN, CONNECTORS, "Pipeline");
    assertThat(entityReferenceDTOPage.getTotalElements()).isEqualTo(7);
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(0), setupUsages.get(0));
    verifyTheValuesAreCorrect(entityReferenceDTOPage.getContent().get(1), setupUsages.get(1));

    Page<EntitySetupUsageDTO> listAPIResponse = entitySetupUsageService.list(
        0, 10, accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier, CONNECTORS, "Pipeline");
    assertThat(listAPIResponse.getTotalElements()).isEqualTo(7);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void saveTest() {
    EntityDetail referredEntity = getEntityDetails(
        referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
    EntityDetail referredByEntity = getEntityDetails(
        referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, SECRETS);
    EntitySetupUsageDTO entitySetupUsageDTO =
        createEntityReference(accountIdentifier, referredEntity, referredByEntity);
    EntitySetupUsageDTO savedEntitySetupUsageDTO = entitySetupUsageService.save(entitySetupUsageDTO);
    verifyTheValuesAreCorrect(savedEntitySetupUsageDTO, entitySetupUsageDTO);
  }

  private void verifyTheValuesAreCorrect(
      EntitySetupUsageDTO actualEntitySetupUsageDTO, EntitySetupUsageDTO expectedEntitySetupUsageDTO) {
    assertThat(actualEntitySetupUsageDTO).isNotNull();
    assertThat(actualEntitySetupUsageDTO.getReferredEntity())
        .isEqualTo(expectedEntitySetupUsageDTO.getReferredEntity());
    assertThat(actualEntitySetupUsageDTO.getReferredEntity().getType())
        .isEqualTo(expectedEntitySetupUsageDTO.getReferredEntity().getType());
    assertThat(actualEntitySetupUsageDTO.getReferredEntity().getName())
        .isEqualTo(expectedEntitySetupUsageDTO.getReferredEntity().getName());

    assertThat(actualEntitySetupUsageDTO.getReferredByEntity())
        .isEqualTo(expectedEntitySetupUsageDTO.getReferredByEntity());
    assertThat(actualEntitySetupUsageDTO.getReferredByEntity().getType())
        .isEqualTo(expectedEntitySetupUsageDTO.getReferredByEntity().getType());
    assertThat(actualEntitySetupUsageDTO.getReferredByEntity().getName())
        .isEqualTo(expectedEntitySetupUsageDTO.getReferredByEntity().getName());
  }

  private EntitySetupUsageDTO saveEntitySetupUsage() {
    EntityDetail referredEntity = getEntityDetails(
        referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
    EntityDetail referredByEntity = getEntityDetails(
        referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, SECRETS);
    EntitySetupUsageDTO entitySetupUsageDTO1 =
        createEntityReference(accountIdentifier, referredEntity, referredByEntity);
    return entitySetupUsageService.save(entitySetupUsageDTO1);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void deleteTest() {
    String referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredIdentifier);
    String referredByEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredByIdentifier);
    saveEntitySetupUsage();
    boolean isDeleted =
        entitySetupUsageService.delete(accountIdentifier, referredEntityFQN, CONNECTORS, referredByEntityFQN, SECRETS);
    assertThat(isDeleted).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void deleteTestWhenRecordDoesnNotExists() {
    boolean isDeleted = entitySetupUsageService.delete(accountIdentifier, "abc", CONNECTORS, "def", SECRETS);
    assertThat(isDeleted).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void saveNewParticular() {
    EntityDetail referredEntity = getEntityDetails(
        referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
    EntityDetail referredByEntity = getEntityDetails(
        referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, SECRETS);

    final Boolean returned = entitySetupUsageService.flushSave(
        getSetupUsages(accountIdentifier, referredByEntity, referredEntity), CONNECTORS, false, accountIdentifier);
    assertThat(returned).isTrue();
    final Page<EntitySetupUsageDTO> list = entitySetupUsageService.list(0, 10, accountIdentifier, orgIdentifier,
        projectIdentifier, referredEntity.getEntityRef().getIdentifier(), referredEntity.getType(), "");
    assertThat(list.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void saveNewParticularWithOldDelete() {
    EntityDetail referredEntity = getEntityDetails(
        referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
    EntityDetail referredByEntity = getEntityDetails(
        referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, SECRETS);
    entitySetupUsageService.flushSave(
        getSetupUsages(accountIdentifier, referredByEntity, referredEntity), CONNECTORS, false, accountIdentifier);

    EntityDetail referredEntity1 = getEntityDetails(
        referredIdentifier1, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
    EntityDetail referredEntity2 = getEntityDetails(
        referredIdentifier2, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);

    final Boolean returned = entitySetupUsageService.flushSave(
        getSetupUsages(accountIdentifier, referredByEntity, referredEntity, referredEntity1, referredEntity2),
        CONNECTORS, true, accountIdentifier);
    assertThat(returned).isTrue();
    final Page<EntitySetupUsageDTO> list = entitySetupUsageService.list(0, 10, accountIdentifier, orgIdentifier,
        projectIdentifier, referredEntity.getEntityRef().getIdentifier(), referredEntity.getType(), "");
    assertThat(list.getTotalElements()).isEqualTo(1);
    final Page<EntitySetupUsageDTO> list1 = entitySetupUsageService.list(0, 10, accountIdentifier, orgIdentifier,
        projectIdentifier, referredEntity1.getEntityRef().getIdentifier(), referredEntity1.getType(), "");
    assertThat(list1.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void saveNewWithMixedObjectsInChannel() {
    EntityDetail referredByEntity = getEntityDetails(
        referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, SECRETS);
    EntityDetail referredEntity = getEntityDetails(
        referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
    EntityDetail referredEntity1 = getEntityDetails(
        referredIdentifier1, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, PIPELINES);
    EntityDetail referredEntity2 = getEntityDetails(
        referredIdentifier2, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);

    final Boolean returned = entitySetupUsageService.flushSave(
        getSetupUsages(accountIdentifier, referredByEntity, referredEntity, referredEntity1, referredEntity2),
        CONNECTORS, true, accountIdentifier);
    assertThat(returned).isTrue();
    final Page<EntitySetupUsageDTO> list = entitySetupUsageService.list(0, 10, accountIdentifier, orgIdentifier,
        projectIdentifier, referredEntity.getEntityRef().getIdentifier(), referredEntity.getType(), "");
    assertThat(list.getTotalElements()).isEqualTo(1);
    final Page<EntitySetupUsageDTO> list1 = entitySetupUsageService.list(0, 10, accountIdentifier, orgIdentifier,
        projectIdentifier, referredEntity1.getEntityRef().getIdentifier(), referredEntity1.getType(), "");
    assertThat(list1.getTotalElements()).isEqualTo(0);
  }

  private List<EntitySetupUsage> getSetupUsages(
      String accountIdentifier, EntityDetail referredByEntity, EntityDetail... referredEntities) {
    return Arrays.stream(referredEntities)
        .map(referredEntity
            -> EntitySetupUsage.builder()
                   .accountIdentifier(accountIdentifier)
                   .referredByEntity(referredByEntity)
                   .referredByEntityFQN(referredByEntity.getEntityRef().getFullyQualifiedName())
                   .referredByEntityType(referredByEntity.getType().toString())
                   .referredEntity(referredEntity)
                   .referredEntityFQN(referredEntity.getEntityRef().getFullyQualifiedName())
                   .referredEntityType(referredEntity.getType().toString())
                   .build())
        .collect(Collectors.toList());
  }

  @Test
  @Owner(developers = OwnerRule.HARI)
  @Category(UnitTests.class)
  public void listBatchTest() {
    List<EntitySetupUsageDTO> setupUsages = new ArrayList<>();
    String referredByIdentifier = "referredIdentifier";
    String referredByEntityName = "Pipeline";
    for (int i = 0; i < 2; i++) {
      String referredIdentifier = "referredIdentifier" + i;
      String referredEntityName = "Connector" + i;
      EntityDetail referredByEntity = getEntityDetails(
          referredByIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES);
      EntityDetail referredEntity = getEntityDetails(
          referredIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
      EntitySetupUsageDTO entitySetupUsageDTO =
          createEntityReference(accountIdentifier, referredEntity, referredByEntity);
      setupUsages.add(entitySetupUsageDTO);
      entitySetupUsageService.save(entitySetupUsageDTO);
    }

    // Adding one extra setup usage for different entity
    String referredByIdentifier1 = "referredByIdentifier1";
    String referredIdentifier1 = "referredIdentifier1";
    EntityDetail referredByEntity = getEntityDetails(
        referredByIdentifier1, accountIdentifier, orgIdentifier, projectIdentifier, referredByEntityName, PIPELINES);
    EntityDetail referredEntity = getEntityDetails(
        referredIdentifier1, accountIdentifier, orgIdentifier, projectIdentifier, referredEntityName, CONNECTORS);
    EntitySetupUsageDTO entitySetupUsageDTO =
        createEntityReference(accountIdentifier, referredEntity, referredByEntity);
    setupUsages.add(entitySetupUsageDTO);
    entitySetupUsageService.save(entitySetupUsageDTO);
    String referredByEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredByIdentifier);
    String referredByEntityFQN1 = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, referredByIdentifier1);
    final EntityReferencesDTO entityReferencesDTO = entitySetupUsageService.listAllReferredUsagesBatch(
        accountIdentifier, Arrays.asList(referredByEntityFQN, referredByEntityFQN1), PIPELINES, CONNECTORS);
    assertThat(entityReferencesDTO.getEntitySetupUsageBatchList().size() == 2);
    assertThat(
        entityReferencesDTO.getEntitySetupUsageBatchList().get(0).getReferredByEntity().equals(referredByEntityFQN)
        && entityReferencesDTO.getEntitySetupUsageBatchList().get(0).getReferredEntities().size() == 2);
    assertThat(
        entityReferencesDTO.getEntitySetupUsageBatchList().get(1).getReferredByEntity().equals(referredByEntityFQN1)
        && entityReferencesDTO.getEntitySetupUsageBatchList().get(1).getReferredEntities().size() == 1);
  }

  @Test
  @Owner(developers = OwnerRule.VLAD)
  @Category(UnitTests.class)
  public void verifyListAllWhenReferreByTypeIsEmpty() {
    List<String> result = entitySetupUsageService.listAllReferredEntityIdentifiersPerReferredEntityScope(
        Scope.of(accountIdentifier, null, null), referredByEntityName, EntityType.FILES, null, null, null);
    assertThat(result).isEmpty();
  }
}
