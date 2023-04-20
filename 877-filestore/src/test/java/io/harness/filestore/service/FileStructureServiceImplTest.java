/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.FileBucket.FILE_STORE;
import static io.harness.filestore.FileStoreTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.ORG_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.PROJECT_IDENTIFIER;
import static io.harness.filestore.utils.FileStoreNodeUtils.getFSNodesIdentifiers;
import static io.harness.filestore.utils.FileStoreNodeUtils.getSubFolder;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createCriteriaByScopeAndParentIdentifier;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.impl.FileStructureServiceImpl;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.repositories.spring.FileStoreRepository;
import io.harness.rule.Owner;

import software.wings.service.intfc.FileService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileStructureServiceImplTest extends CategoryTest {
  @Mock private FileStoreRepository fileStoreRepository;
  @Mock private FileService fileService;

  @InjectMocks private FileStructureServiceImpl fileStructureService;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileContent() {
    String fileUuid = "fileUuid";
    final ArgumentCaptor<String> fileUuidArgumentCaptor = ArgumentCaptor.forClass(String.class);

    fileStructureService.getFileContent(fileUuid);

    verify(fileService, times(1)).downloadToStream(fileUuidArgumentCaptor.capture(), any(), any());
    String captorValue = fileUuidArgumentCaptor.getValue();
    assertThat(captorValue).isNotNull();
    assertThat(captorValue).isEqualTo(fileUuid);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileContentWitException() {
    fileStructureService.getFileContent("fileUuid");
    assertThatThrownBy(() -> fileStructureService.getFileContent(""))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("File UUID cannot be null or empty");
  }

  /**
   * Root -
   *       file1
   *       file2
   *       folder1 -
   *                file3
   *                file4
   *                folder2 -
   *                         file5
   *                         file6
   *       folder3 -
   *                file7
   *                file8
   *                folder4 -
   *                         folder5 -
   *                                  file9
   *                                  file10
   *       emptyFolder
   *
   */
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFolderTreeStructure() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    FolderNodeDTO rootFolder = FolderNodeDTO.builder().identifier("Root").name("Root").build();

    when(fileStoreRepository.findAllAndSort(eq(createCriteriaByScopeAndParentIdentifier(scope, "Root")), any()))
        .thenReturn(Arrays.asList(
            NGFile.builder().type(NGFileType.FILE).name("file1").identifier("file1").fileUuid("file1Uuid").build(),
            NGFile.builder().type(NGFileType.FILE).name("file2").identifier("file2").fileUuid("file2Uuid").build(),
            NGFile.builder().type(NGFileType.FOLDER).name("folder1").identifier("folder1").build(),
            NGFile.builder().type(NGFileType.FOLDER).name("folder3").identifier("folder3").build(),
            NGFile.builder().type(NGFileType.FOLDER).name("emptyfolder").identifier("emptyfolder").build()));

    when(fileStoreRepository.findAllAndSort(eq(createCriteriaByScopeAndParentIdentifier(scope, "folder1")), any()))
        .thenReturn(Arrays.asList(
            NGFile.builder().type(NGFileType.FILE).name("file3").identifier("file3").fileUuid("file3Uuid").build(),
            NGFile.builder().type(NGFileType.FILE).name("file4").identifier("file4").fileUuid("file4Uuid").build(),
            NGFile.builder().type(NGFileType.FOLDER).name("folder2").identifier("folder2").build()));

    when(fileStoreRepository.findAllAndSort(eq(createCriteriaByScopeAndParentIdentifier(scope, "folder2")), any()))
        .thenReturn(Arrays.asList(
            NGFile.builder().type(NGFileType.FILE).name("file5").identifier("file5").fileUuid("file5Uuid").build(),
            NGFile.builder().type(NGFileType.FILE).name("file6").identifier("file6").fileUuid("file6Uuid").build()));

    when(fileStoreRepository.findAllAndSort(eq(createCriteriaByScopeAndParentIdentifier(scope, "folder3")), any()))
        .thenReturn(Arrays.asList(
            NGFile.builder().type(NGFileType.FILE).name("file7").identifier("file7").fileUuid("file7Uuid").build(),
            NGFile.builder().type(NGFileType.FILE).name("file8").identifier("file8").fileUuid("file8Uuid").build(),
            NGFile.builder().type(NGFileType.FOLDER).name("folder4").identifier("folder4").build()));

    when(fileStoreRepository.findAllAndSort(eq(createCriteriaByScopeAndParentIdentifier(scope, "folder4")), any()))
        .thenReturn(Collections.singletonList(
            NGFile.builder().type(NGFileType.FOLDER).name("folder5").identifier("folder5").build()));

    when(fileStoreRepository.findAllAndSort(eq(createCriteriaByScopeAndParentIdentifier(scope, "folder5")), any()))
        .thenReturn(Arrays.asList(
            NGFile.builder().type(NGFileType.FILE).name("file9").identifier("file9").fileUuid("file9Uuid").build(),
            NGFile.builder().type(NGFileType.FILE).name("file10").identifier("file10").fileUuid("file10Uuid").build()));

    doNothing().when(fileService).downloadToStream(any(String.class), any(), eq(FILE_STORE));

    fileStructureService.createFolderTreeStructure(rootFolder, scope, true);

    assertThat(getFSNodesIdentifiers(rootFolder.getChildren()))
        .contains("file1", "file2", "folder1", "folder3", "emptyfolder");

    FolderNodeDTO folder1 = getFolderChildren(rootFolder, "folder1");
    FolderNodeDTO folder3 = getFolderChildren(rootFolder, "folder3");
    FolderNodeDTO emptyFolder = getFolderChildren(rootFolder, "emptyfolder");
    FolderNodeDTO nullFolder = getFolderChildren(rootFolder, null);

    assertThat(getFSNodesIdentifiers(folder1.getChildren())).contains("file3", "file4", "folder2");
    assertThat(getFSNodesIdentifiers(folder3.getChildren())).contains("file7", "file8", "folder4");
    assertThat(getFSNodesIdentifiers(emptyFolder.getChildren())).isEmpty();
    assertThat(nullFolder).isNull();

    FolderNodeDTO folder2 = getFolderChildren(folder1, "folder2");
    FolderNodeDTO folder4 = getFolderChildren(folder3, "folder4");

    assertThat(getFSNodesIdentifiers(folder2.getChildren())).contains("file5", "file6");
    assertThat(getFSNodesIdentifiers(folder4.getChildren())).contains("folder5");

    FolderNodeDTO folder5 = getFolderChildren(folder4, "folder5");
    assertThat(getFSNodesIdentifiers(folder5.getChildren())).contains("file9", "file10");
  }

  /**
   * Root -
   *       file1
   *       file2
   *       folder1
   *       folder2
   */
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFolderTreeStructureWithException() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    FolderNodeDTO rootFolder = FolderNodeDTO.builder().identifier("Root").name("Root").build();

    when(fileStoreRepository.findAllAndSort(eq(createCriteriaByScopeAndParentIdentifier(scope, "Root")), any()))
        .thenReturn(Arrays.asList(
            NGFile.builder().type(NGFileType.FILE).name("file1").identifier("file1").fileUuid("file1Uuid").build(),
            NGFile.builder().type(NGFileType.FILE).name("file2").identifier("file2").fileUuid("file2Uuid").build(),
            NGFile.builder().type(NGFileType.FOLDER).name("folder1").identifier("folder1").build(),
            NGFile.builder().type(NGFileType.FOLDER).name("folder2").identifier("folder2").build()));

    doThrow(new InvalidRequestException("Unable to download file"))
        .when(fileService)
        .downloadToStream(any(String.class), any(), eq(FILE_STORE));

    assertThatThrownBy(() -> fileStructureService.createFolderTreeStructure(rootFolder, scope, true))
        .isInstanceOf(UnexpectedException.class)
        .hasMessage(
            "Unexpected folder tree structure creation  error: io.harness.exception.InvalidRequestException: Unable to download file");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void shouldReturn() {
    // Given
    when(fileStoreRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifierNotAndPathStartsWith(
                 any(), any(), any(), any(), any(), any()))
        .thenReturn(Arrays.asList(NGFile.builder()
                                      .accountIdentifier("acc1")
                                      .orgIdentifier("org1")
                                      .projectIdentifier("proj1")
                                      .identifier("ident1")
                                      .build(),
            NGFile.builder()
                .accountIdentifier("acc2")
                .orgIdentifier("org2")
                .projectIdentifier("proj2")
                .identifier("ident2")
                .build()));

    // When
    List<String> childrenFqns = fileStructureService.listFolderChildrenFQNs(NGFile.builder().build());

    // Then
    assertThat(childrenFqns).isNotNull().containsExactly("acc1/org1/proj1/ident1", "acc2/org2/proj2/ident2");
  }

  private FolderNodeDTO getFolderChildren(FolderNodeDTO parentFolder, String folderName) {
    return getSubFolder(parentFolder, folderName).orElse(null);
  }
}
