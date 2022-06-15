/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.FileStoreTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.ORG_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.PROJECT_IDENTIFIER;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.SearchPageParams;
import io.harness.category.element.UnitTests;
import io.harness.exception.ReferencedEntityException;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.impl.FileReferenceServiceImpl;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage.EntitySetupUsageKeys;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileReferenceServiceTest extends CategoryTest {
  @Mock private EntitySetupUsageService entitySetupUsageService;
  @Mock private FileStructureService fileStructureService;

  @InjectMocks private FileReferenceServiceImpl fileReferenceService;

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldVerifyFileNotReferencedByOtherEntities() {
    NGFile file = NGFile.builder().identifier("testFile").accountIdentifier(ACCOUNT_IDENTIFIER).build();
    Long result = fileReferenceService.countEntitiesReferencingFile(file);
    assertThat(result).isEqualTo(0l);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldVerifyFileIsReferencedByOtherEntities() {
    String identifier = "testFile";
    NGFile file = NGFile.builder().identifier(identifier).accountIdentifier(ACCOUNT_IDENTIFIER).build();
    Long count = 123l;
    when(entitySetupUsageService.referredByEntityCount(
             ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER + "/" + identifier, EntityType.FILES))
        .thenReturn(count);
    Long result = fileReferenceService.countEntitiesReferencingFile(file);
    assertThat(result).isEqualTo(count);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldFetchReferencedBy() {
    String identifier = "testFile";
    NGFile file = NGFile.builder().identifier(identifier).accountIdentifier(ACCOUNT_IDENTIFIER).build();
    SearchPageParams searchPageParams = SearchPageParams.builder().page(1).size(10).build();
    Page<EntitySetupUsageDTO> references = mock(Page.class);
    when(entitySetupUsageService.listAllEntityUsage(searchPageParams.getPage(), searchPageParams.getSize(),
             file.getAccountIdentifier(), ACCOUNT_IDENTIFIER + "/" + identifier, EntityType.FILES, EntityType.PIPELINES,
             searchPageParams.getSearchTerm(),
             Sort.by(Sort.Direction.ASC, FileReferenceServiceImpl.REFERRED_BY_IDENTIFIER_KEY)))
        .thenReturn(references);
    Page<EntitySetupUsageDTO> result =
        fileReferenceService.getReferencedBy(searchPageParams, file, EntityType.PIPELINES);
    assertThat(result).isEqualTo(references);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldVerifyFolderIsReferencedByOtherEntities() {
    String identifier1 = "testFolder1";
    NGFile folder1 =
        NGFile.builder().identifier(identifier1).accountIdentifier(ACCOUNT_IDENTIFIER).type(NGFileType.FOLDER).build();

    when(entitySetupUsageService.countReferredByEntitiesByFQNsIn(any(), any())).thenReturn(2L);
    when(fileStructureService.listFolderChildrenFQNs(any())).thenReturn(Lists.newArrayList());

    assertThatThrownBy(() -> fileReferenceService.validateReferenceByAndThrow(folder1))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage("Folder [testFolder1], or its subfolders, contain file(s) referenced by " + 2
            + " other entities and can not be deleted.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldFetchReferencedByForScope() {
    String referredEntityFQScope = IdentifierRef.builder()
                                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJECT_IDENTIFIER)
                                       .build()
                                       .getFullyQualifiedScopeIdentifier();
    SearchPageParams searchPageParams = SearchPageParams.builder().page(1).size(10).build();
    EntitySetupUsageDTO entitySetupUsageDTO = EntitySetupUsageDTO.builder().build();
    List<EntitySetupUsageDTO> references = Arrays.asList(entitySetupUsageDTO);
    String entityName = "EntityName";

    when(entitySetupUsageService.listAllEntityUsagePerReferredEntityScope(
             Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), referredEntityFQScope, EntityType.FILES,
             EntityType.PIPELINES, entityName, Sort.by(Sort.Direction.ASC, EntitySetupUsageKeys.referredByEntityName)))
        .thenReturn(references);

    List<EntitySetupUsageDTO> result = fileReferenceService.getAllReferencedByInScope(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, searchPageParams, EntityType.PIPELINES, entityName);
    assertThat(result).containsExactly(entitySetupUsageDTO);
  }
}
