/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.FileStoreTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.NGTemplateReference;
import io.harness.beans.Scope;
import io.harness.beans.SearchPageParams;
import io.harness.category.element.UnitTests;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.impl.FileReferenceServiceImpl;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
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
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldVerifyThatExceptionIsThrownCountEntitiesReferencingFile() {
    String identifier = "testFile";
    NGFile file = NGFile.builder().identifier(identifier).accountIdentifier(ACCOUNT_IDENTIFIER).build();
    when(entitySetupUsageService.referredByEntityCount(
             ACCOUNT_IDENTIFIER, ACCOUNT_IDENTIFIER + "/" + identifier, EntityType.FILES))
        .thenThrow(IllegalArgumentException.class);
    assertThatThrownBy(() -> fileReferenceService.countEntitiesReferencingFile(file))
        .isInstanceOf(UnexpectedException.class);
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
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldVerifyFolderIsReferencedBy() {
    String identifier1 = "testFolder1";
    NGFile folder1 =
        NGFile.builder().identifier(identifier1).accountIdentifier(ACCOUNT_IDENTIFIER).type(NGFileType.FOLDER).build();
    String folderFqn = ACCOUNT_IDENTIFIER.concat("/").concat(identifier1);
    when(fileStructureService.listFolderChildrenFQNs(any())).thenReturn(Lists.newArrayList());
    when(entitySetupUsageService.referredByEntityCount(ACCOUNT_IDENTIFIER, folderFqn, EntityType.FILES)).thenReturn(1L);

    assertThatThrownBy(() -> fileReferenceService.validateReferenceByAndThrow(folder1))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage("Folder [testFolder1] is referenced by " + 1 + " other entities and can not be deleted.");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldValidateFileIsReferencedByOtherEntities() {
    String identifier1 = "testFile1";
    NGFile file1 =
        NGFile.builder().identifier(identifier1).accountIdentifier(ACCOUNT_IDENTIFIER).type(NGFileType.FILE).build();

    when(entitySetupUsageService.referredByEntityCount(any(), any(), any())).thenReturn(2L);

    assertThatThrownBy(() -> fileReferenceService.validateReferenceByAndThrow(file1))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage("File [testFile1] is referenced by 2 other entities and can not be deleted.");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldGetAllFileIdentifiersReferencedByInScope() {
    // Given
    Scope scope = Scope.of("account-ident", "org-ident", "proj-ident");
    EntityReference reference = NGTemplateReference.builder().identifier("ident").build();

    EntitySetupUsageDTO entitySetupUsageDTO =
        EntitySetupUsageDTO.builder().referredEntity(EntityDetail.builder().entityRef(reference).build()).build();

    when(entitySetupUsageService.listAllReferredEntityIdentifiersPerReferredEntityScope(
             any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.singletonList("ident"));

    // When
    List<String> result =
        fileReferenceService.getAllFileIdentifiersReferencedByInScope(scope, EntityType.FILES, "referredby");

    // Then
    assertThat(result).isNotNull().contains("ident");
  }
}
