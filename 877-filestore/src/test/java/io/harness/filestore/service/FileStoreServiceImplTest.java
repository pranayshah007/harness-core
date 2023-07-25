/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import static io.harness.FileStoreConstants.ROOT_FOLDER_IDENTIFIER;
import static io.harness.FileStoreConstants.ROOT_FOLDER_NAME;
import static io.harness.filestore.FileStoreTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.ADMIN_USER_EMAIL;
import static io.harness.filestore.FileStoreTestConstants.ADMIN_USER_NAME;
import static io.harness.filestore.FileStoreTestConstants.FILE_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.FILE_UUID;
import static io.harness.filestore.FileStoreTestConstants.FOLDER_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.FOLDER_NAME;
import static io.harness.filestore.FileStoreTestConstants.IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.INVALID_ACCOUNT_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.ORG_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.PARENT_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.PROJECT_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.YML_MIME_TYPE;
import static io.harness.filestore.entities.NGFile.builder;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createCriteriaByScopeAndParentIdentifier;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createFilesFilterCriteria;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createSortByLastModifiedAtDesc;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLAD;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.Scope;
import io.harness.beans.SearchPageParams;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ChecksumType;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.file.beans.NGBaseFile;
import io.harness.filestore.FileStoreConfiguration;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.filestore.dto.filter.ReferencedByDTO;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.impl.FileReferenceServiceImpl;
import io.harness.filestore.service.impl.FileStoreServiceImpl;
import io.harness.filestore.service.impl.FileValidationServiceImpl;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.filestore.dto.FileFilterDTO;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.repositories.spring.FileStoreRepository;
import io.harness.rule.Owner;

import software.wings.service.intfc.FileService;

import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileStoreServiceImplTest extends CategoryTest {
  @Mock private FileStoreRepository fileStoreRepository;
  @Mock private FileService fileService;
  @Mock private FileStoreConfiguration configuration;
  @Mock private FileReferenceServiceImpl fileReferenceService;
  @Mock private FileFailsafeService fileFailsafeService;
  @Mock private FileStructureService fileStructureService;
  @Mock private AccountService accountService;
  @Mock private OrganizationService organizationService;
  @Mock private ProjectService projectService;

  @Spy @InjectMocks private FileValidationService fileValidationService = new FileValidationServiceImpl();

  @InjectMocks private FileStoreServiceImpl fileStoreService;

  @Before
  public void setup() {
    when(configuration.getFileUploadLimits()).thenReturn(new FileUploadLimit());

    when(fileStoreRepository.save(any())).thenAnswer(invocation -> invocation.getArguments()[0]);

    givenThatDatabaseIsEmpty();

    when(fileReferenceService.countEntitiesReferencingFile(any())).thenReturn(0L);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    NGFile ngFile = createNgFileTypeFile();
    FileDTO fileDto = createFileDto();
    fileDto.setParentIdentifier(ROOT_FOLDER_IDENTIFIER);
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.of(ngFile));
    when(fileFailsafeService.updateAndPublish(any(), any())).thenReturn(fileDto);

    FileDTO result = fileStoreService.update(fileDto, getStreamWithDummyContent());

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("updatedName");
    assertThat(result.getDescription()).isEqualTo("updatedDescription");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testFolderUpdate() {
    NGFile oldFolder = createNgFileTypeFolder();
    FileDTO newFolder = aFolderDto();
    newFolder.setParentIdentifier(ROOT_FOLDER_IDENTIFIER);
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.of(oldFolder));
    when(fileFailsafeService.updateAndPublish(any(), any())).thenReturn(newFolder);
    when(fileStructureService.listFolderChildrenByPath(any())).thenReturn(getNgFiles());

    FileDTO result = fileStoreService.update(newFolder, null);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(newFolder.getName());
    assertThat(result.getDescription()).isEqualTo(newFolder.getDescription());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testUpdateShouldThrowException() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> fileStoreService.update(createFileDto(), null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Not found file/folder with identifier [identifier1], accountIdentifier [null], orgIdentifier [null] and projectIdentifier [null]");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetEmpty() {
    String path = "/folder1/nonExistingFile.yml";
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path))
        .thenReturn(Optional.empty());

    Optional<FileDTO> fileDTO =
        fileStoreService.getByPath(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path);

    assertThat(fileDTO.isPresent()).isFalse();
    assertThat(fileDTO).isEqualTo(Optional.empty());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFile() {
    String path = "/folder1/folder2/test.yml";
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path))
        .thenReturn(Optional.of(createNgFileTypeFile()));

    Optional<FileDTO> fileDTO =
        fileStoreService.getByPath(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path);

    assertThat(fileDTO.isPresent()).isTrue();
    FileDTO file = fileDTO.get();
    assertThat(file.getType()).isEqualTo(NGFileType.FILE);
    assertThat(file.getName()).isEqualTo("oldName");
    assertThat(file.getIdentifier()).isEqualTo(FILE_IDENTIFIER);
    assertThat(file.getLastModifiedBy()).isEqualTo(getEmbeddedUserDetailsDTO());
    assertThat(file.getLastModifiedAt()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFolder() {
    String path = "/some/dummy/path";
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path))
        .thenReturn(Optional.of(createNgFileTypeFolder()));

    Optional<FileDTO> fileDTO =
        fileStoreService.getByPath(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path);

    assertThat(fileDTO.isPresent()).isTrue();
    FileDTO folder = fileDTO.get();
    assertThat(folder.getType()).isEqualTo(NGFileType.FOLDER);
    assertThat(folder.getName()).isEqualTo(FOLDER_NAME);
    assertThat(folder.getIdentifier()).isEqualTo(FOLDER_IDENTIFIER);
    assertThat(folder.getLastModifiedBy()).isEqualTo(getEmbeddedUserDetailsDTO());
    assertThat(folder.getLastModifiedAt()).isEqualTo(1L);
    assertThat(folder.getPath()).isEqualTo("/some/dummy/path");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFileWithNullIdentifier() {
    assertThatThrownBy(
        () -> fileStoreService.downloadFile(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("File identifier cannot be null or empty");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFileWithNotExistingFile() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_IDENTIFIER))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> fileStoreService.downloadFile(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining(format(
            "Not found file/folder with identifier [%s], accountIdentifier [%s], orgIdentifier [%s] and projectIdentifier [%s]",
            FILE_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFileWithFolder() {
    NGFile ngFile = createNgFileTypeFile();
    ngFile.setType(NGFileType.FOLDER);
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_IDENTIFIER))
        .thenReturn(Optional.of(ngFile));

    assertThatThrownBy(
        () -> fileStoreService.downloadFile(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_IDENTIFIER))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining(format("Downloading folder not supported, fileIdentifier: %s", FILE_IDENTIFIER));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFile() {
    NGFile ngFile = createNgFileTypeFile();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_IDENTIFIER))
        .thenReturn(Optional.of(ngFile));
    File fileToReturn = new File("download-file-path");
    when(fileService.download(eq(ngFile.getFileUuid()), any(File.class), any(FileBucket.class)))
        .thenReturn(fileToReturn);

    File file = fileStoreService.downloadFile(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_IDENTIFIER);

    assertThat(file).isNotNull();
    assertThat(file.getPath()).isEqualTo(fileToReturn.getPath());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldSaveNgFile() {
    // Given
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);
    final FileDTO fileDto = aFileDto();
    fileDto.setDraft(false);

    // When
    fileStoreService.create(fileDto, getStreamWithDummyContent());

    // Then
    NGFile expected = builder()
                          .identifier(fileDto.getIdentifier())
                          .accountIdentifier(fileDto.getAccountIdentifier())
                          .description(fileDto.getDescription())
                          .name(fileDto.getName())
                          .type(fileDto.getType())
                          .checksumType(ChecksumType.MD5)
                          .draft(false)
                          .tags(Collections.emptyList())
                          .size(0L)
                          .parentIdentifier(fileDto.getParentIdentifier())
                          .path("/Root/folder/file-name")
                          .build();

    verify(fileFailsafeService).saveAndPublish(expected);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void shouldSaveNgFileOnOrgLevel() {
    // Given
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);
    final FileDTO fileDto = aFileDto();
    fileDto.setOrgIdentifier(ORG_IDENTIFIER);
    fileDto.setDraft(false);
    when(organizationService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER))
        .thenReturn(Optional.of(
            Organization.builder().accountIdentifier(ACCOUNT_IDENTIFIER).identifier(ORG_IDENTIFIER).build()));

    // When
    fileStoreService.create(fileDto, getStreamWithDummyContent());

    // Then
    NGFile expected = builder()
                          .identifier(fileDto.getIdentifier())
                          .accountIdentifier(fileDto.getAccountIdentifier())
                          .orgIdentifier(ORG_IDENTIFIER)
                          .description(fileDto.getDescription())
                          .name(fileDto.getName())
                          .type(fileDto.getType())
                          .checksumType(ChecksumType.MD5)
                          .draft(false)
                          .tags(Collections.emptyList())
                          .size(0L)
                          .parentIdentifier(fileDto.getParentIdentifier())
                          .path("/Root/folder/file-name")
                          .build();

    verify(fileFailsafeService).saveAndPublish(expected);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void shouldSaveNgFileOnProjectLevel() {
    // Given
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);
    final FileDTO fileDto = aFileDto();
    fileDto.setOrgIdentifier(ORG_IDENTIFIER);
    fileDto.setProjectIdentifier(PROJECT_IDENTIFIER);
    fileDto.setDraft(false);
    when(projectService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(Optional.of(Project.builder()
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .identifier(ORG_IDENTIFIER)
                                    .identifier(PROJECT_IDENTIFIER)
                                    .build()));

    // When
    fileStoreService.create(fileDto, getStreamWithDummyContent());

    // Then
    NGFile expected = builder()
                          .identifier(fileDto.getIdentifier())
                          .accountIdentifier(fileDto.getAccountIdentifier())
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJECT_IDENTIFIER)
                          .description(fileDto.getDescription())
                          .name(fileDto.getName())
                          .type(fileDto.getType())
                          .checksumType(ChecksumType.MD5)
                          .draft(false)
                          .tags(Collections.emptyList())
                          .size(0L)
                          .parentIdentifier(fileDto.getParentIdentifier())
                          .path("/Root/folder/file-name")
                          .build();

    verify(fileFailsafeService).saveAndPublish(expected);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldSaveFileUsingFileService() {
    // Given
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);
    final FileDTO fileDto = aFileDto();

    // When
    fileStoreService.create(fileDto, getStreamWithDummyContent());

    // Then
    NGBaseFile baseFile = new NGBaseFile();
    baseFile.setFileName(fileDto.getName());
    baseFile.setAccountId(fileDto.getAccountIdentifier());

    verify(fileService).saveFile(eq(baseFile), notNull(InputStream.class), eq(FileBucket.FILE_STORE));
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldNotInvokeSaveFileOnFileServiceForFolder() {
    // Given
    givenThatFileNotExistInDB();
    final FileDTO folderDto = aFolderDto();
    folderDto.setParentIdentifier(ROOT_FOLDER_IDENTIFIER);

    // When
    fileStoreService.create(folderDto, null);

    // Then
    verifyNoInteractions(fileService);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldNotInvokeSaveFileOnFileServiceForDraftFile() {
    // Given
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);
    final FileDTO fileDto = aFileDto();
    fileDto.setDraft(true);

    // When
    fileStoreService.create(fileDto, getStreamWithDummyContent());

    // Then
    verifyNoInteractions(fileService);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldSaveDraftNgFile() {
    // Given
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);
    final FileDTO fileDto = aFileDto();
    fileDto.setDraft(true);

    // When
    fileStoreService.create(fileDto, getStreamWithDummyContent());

    // Then
    NGFile expected = NGFile.builder()
                          .identifier(fileDto.getIdentifier())
                          .accountIdentifier(fileDto.getAccountIdentifier())
                          .description(fileDto.getDescription())
                          .name(fileDto.getName())
                          .type(fileDto.getType())
                          .draft(true)
                          .tags(Collections.emptyList())
                          .parentIdentifier(fileDto.getParentIdentifier())
                          .path("/Root/folder/file-name")
                          .build();

    verify(fileFailsafeService).saveAndPublish(expected);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenFolderAlreadyExistsInDatabase() {
    givenThatFileExistsInDatabase();

    FileDTO folderDto = aFolderDto();

    assertThatThrownBy(() -> fileStoreService.create(folderDto, null))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining(
            "Try another identifier, folder with identifier [identifier] already exists.", folderDto.getIdentifier());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldHandleDuplicateIdentifierExceptionForFile() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.of(builder().build()));
    FileDTO fileDTO = aFileDto();

    assertThatThrownBy(() -> fileStoreService.create(fileDTO, getStreamWithDummyContent()))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining("Try another identifier, file with identifier [fileIdentifier] already exists.");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldHandleDuplicateNameExceptionForFile() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
             any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(builder().build()));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    FileDTO fileDTO = aFileDto();

    assertThatThrownBy(() -> fileStoreService.create(fileDTO, getStreamWithDummyContent()))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining(
            "Try another name, file with name [file-name] already exists in the parent folder [parentIdentifier].",
            fileDTO.getIdentifier());
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDeleteFileDoesNotExist() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
             any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.empty());
    assertThatThrownBy(
        () -> fileStoreService.delete(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Not found file/folder with identifier [identifier], accountIdentifier [accountIdentifier], orgIdentifier [orgIdentifier] and projectIdentifier [projectIdentifier]");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDeleteFile() {
    String fileUuid = "fileUUID";
    NGFile file = builder().name(IDENTIFIER).identifier(IDENTIFIER).fileUuid(fileUuid).build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(file));
    when(fileFailsafeService.deleteAndPublish(any(), anyBoolean())).thenReturn(true);
    boolean result = fileStoreService.delete(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER, false);
    assertThat(result).isTrue();
    verify(fileFailsafeService).deleteAndPublish(file, false);
    verify(fileService).deleteFile(fileUuid, FileBucket.FILE_STORE);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDeleteFolder() {
    String fileUuid = "fileUUID";
    NGFile file = builder().name(IDENTIFIER).fileUuid(fileUuid).build();
    String folder1 = "folder1";
    NGFile parentFolder = builder()
                              .name(folder1)
                              .identifier(folder1)
                              .type(NGFileType.FOLDER)
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(file));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Optional.of(parentFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Arrays.asList(file));
    when(fileFailsafeService.deleteAndPublish(any(), anyBoolean())).thenReturn(true);
    boolean result = fileStoreService.delete(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1, false);
    assertThat(result).isTrue();
    verify(fileFailsafeService).deleteAndPublish(file, false);
    verify(fileFailsafeService).deleteAndPublish(parentFolder, false);
    verify(fileService).deleteFile(fileUuid, FileBucket.FILE_STORE);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldNotDeleteFolderWithReferences() {
    String fileUuid = "fileUUID";
    NGFile file = builder().name(IDENTIFIER).fileUuid(fileUuid).build();
    String folder1 = "folder1";
    NGFile parentFolder = builder()
                              .name(folder1)
                              .identifier(folder1)
                              .type(NGFileType.FOLDER)
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(file));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Optional.of(parentFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Arrays.asList(file));
    when(fileFailsafeService.deleteAndPublish(any(), anyBoolean())).thenReturn(true);
    doThrow(
        new ReferencedEntityException(format(
            "Folder [%s], or its subfolders, contain file(s) referenced by %s other entities and can not be deleted.",
            parentFolder.getIdentifier(), 1L)))
        .when(fileReferenceService)
        .validateReferenceByAndThrow(parentFolder);
    assertThatThrownBy(
        () -> fileStoreService.delete(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1, false))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage(
            "Folder [folder1], or its subfolders, contain file(s) referenced by 1 other entities and can not be deleted.");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldForceDeleteFolderWithReferences() {
    String fileUuid = "fileUUID";
    NGFile file = builder().name(IDENTIFIER).fileUuid(fileUuid).build();
    String folder1 = "folder1";
    NGFile parentFolder = builder()
                              .name(folder1)
                              .identifier(folder1)
                              .type(NGFileType.FOLDER)
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(file));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Optional.of(parentFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Arrays.asList(file));
    when(fileFailsafeService.deleteAndPublish(any(), anyBoolean())).thenReturn(true);
    doThrow(
        new ReferencedEntityException(format(
            "Folder [%s], or its subfolders, contain file(s) referenced by %s other entities and can not be deleted.",
            parentFolder.getIdentifier(), 1L)))
        .when(fileReferenceService)
        .validateReferenceByAndThrow(parentFolder);

    boolean result = fileStoreService.delete(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1, true);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDeleteFolderWithSubfolder() {
    String fileUuid1 = "fileUUID1";
    NGFile file = builder().name(IDENTIFIER).identifier(IDENTIFIER).fileUuid(fileUuid1).build();
    String folder1 = "folder1";
    NGFile parentFolder = builder()
                              .name(folder1)
                              .identifier(folder1)
                              .type(NGFileType.FOLDER)
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .build();
    String folder2 = "folder2";
    NGFile childFolder = builder()
                             .name(folder2)
                             .identifier(folder2)
                             .type(NGFileType.FOLDER)
                             .accountIdentifier(ACCOUNT_IDENTIFIER)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJECT_IDENTIFIER)
                             .build();
    String file2 = "file2";
    String fileUuid2 = "fileUUID2";
    NGFile childFile = builder().name(file2).fileUuid(fileUuid2).build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(file));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Optional.of(parentFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder2))
        .thenReturn(Optional.of(childFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, file2))
        .thenReturn(Optional.of(childFile));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1))
        .thenReturn(Arrays.asList(file, childFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder2))
        .thenReturn(Arrays.asList(childFile));
    when(fileFailsafeService.deleteAndPublish(any(), anyBoolean())).thenReturn(true);
    boolean result = fileStoreService.delete(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folder1, false);
    assertThat(result).isTrue();
    verify(fileFailsafeService).deleteAndPublish(file, false);
    verify(fileFailsafeService).deleteAndPublish(parentFolder, false);
    verify(fileFailsafeService).deleteAndPublish(childFile, false);
    verify(fileFailsafeService).deleteAndPublish(childFolder, false);
    verify(fileService).deleteFile(fileUuid1, FileBucket.FILE_STORE);
    verify(fileService).deleteFile(fileUuid2, FileBucket.FILE_STORE);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDeleteFolderWithScopePrefix() {
    String fileUuid = "fileUUID";
    NGFile file = builder().name(IDENTIFIER).identifier(IDENTIFIER).fileUuid(fileUuid).build();
    String folder1 = "folder1";
    NGFile parentFolder = builder()
                              .name(folder1)
                              .identifier(folder1)
                              .type(NGFileType.FOLDER)
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, null, null, IDENTIFIER))
        .thenReturn(Optional.of(file));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, null, null, folder1))
        .thenReturn(Optional.of(parentFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
             ACCOUNT_IDENTIFIER, null, null, folder1))
        .thenReturn(Arrays.asList(file));
    assertThatThrownBy(()
                           -> fileStoreService.delete(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "account." + folder1, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Not found file/folder with identifier [account.folder1], accountIdentifier [accountIdentifier], orgIdentifier [orgIdentifier] and projectIdentifier [projectIdentifier]");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDeleteRootFolder() {
    String rootIdentifier = ROOT_FOLDER_IDENTIFIER;
    assertThatThrownBy(
        () -> fileStoreService.delete(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, rootIdentifier, false))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Root folder [Root] can not be deleted.");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFolderNodes() {
    FolderNodeDTO folderNodeDTO = FolderNodeDTO.builder().identifier(FOLDER_IDENTIFIER).name(FOLDER_NAME).build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FOLDER_IDENTIFIER))
        .thenReturn(Optional.of(createNgFileTypeFolder()));
    when(fileStoreRepository.findAllAndSort(
             createCriteriaByScopeAndParentIdentifier(
                 Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), FOLDER_IDENTIFIER),
             createSortByLastModifiedAtDesc()))
        .thenReturn(getNgFiles());

    FolderNodeDTO populatedFolderNodeDTO =
        fileStoreService.listFolderNodes(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folderNodeDTO, null);

    assertThat(populatedFolderNodeDTO).isNotNull();
    assertThat(populatedFolderNodeDTO.getName()).isEqualTo(FOLDER_NAME);
    assertThat(populatedFolderNodeDTO.getIdentifier()).isEqualTo(FOLDER_IDENTIFIER);
    assertThat(populatedFolderNodeDTO.getChildren().size()).isEqualTo(3);
    assertThat(populatedFolderNodeDTO.getChildren())
        .contains(FileNodeDTO.builder().name("fileName").identifier("fileIdentifier").build(),
            FolderNodeDTO.builder().name("folderName1").identifier("folderIdentifier1").build(),
            FolderNodeDTO.builder().name("folderName2").identifier("folderIdentifier2").build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFolderNodesOnPath() {
    String path = "/folder1/fileName";
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path))
        .thenReturn(Optional.of(getNgFile()));
    when(fileStoreRepository.findAllAndSort(
             createCriteriaByScopeAndParentIdentifier(
                 Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), ROOT_FOLDER_IDENTIFIER),
             createSortByLastModifiedAtDesc()))
        .thenReturn(getRootNgFiles());
    when(fileStoreRepository.findAllAndSort(
             createCriteriaByScopeAndParentIdentifier(
                 Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), "folder1"),
             createSortByLastModifiedAtDesc()))
        .thenReturn(getFolder1NgFiles());

    FolderNodeDTO populatedFolderNodeDTO =
        fileStoreService.listFileStoreNodesOnPath(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path, null);

    assertThat(populatedFolderNodeDTO.getIdentifier()).isEqualTo(ROOT_FOLDER_IDENTIFIER);
    assertThat(populatedFolderNodeDTO.getChildren().size()).isEqualTo(1);
    List<FileStoreNodeDTO> children = populatedFolderNodeDTO.getChildren();
    FolderNodeDTO folder1 = (FolderNodeDTO) children.get(0);
    assertThat(folder1.getIdentifier()).isEqualTo("folder1");
    assertThat(folder1.getPath()).isEqualTo("/folder1");

    List<FileStoreNodeDTO> folder1Children = folder1.getChildren();
    assertThat(folder1Children.size()).isEqualTo(2);

    FolderNodeDTO folder2 = (FolderNodeDTO) folder1Children.get(0);
    assertThat(folder2.getIdentifier()).isEqualTo("folder2");
    assertThat(folder2.getPath()).isEqualTo("/folder1/folder2");

    FileNodeDTO file = (FileNodeDTO) folder1Children.get(1);
    assertThat(file.getIdentifier()).isEqualTo("fileIdentifier");
    assertThat(file.getName()).isEqualTo("fileName");
    assertThat(file.getPath()).isEqualTo("/folder1/fileName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFolderNodesOnPathWithNotValidPath() {
    String path = "folder1/fileName";

    assertThatThrownBy(()
                           -> fileStoreService.listFileStoreNodesOnPath(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Invalid file path, path: folder1/fileName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFolderNodesOnPathWithNotFoundFile() {
    String path = "/folder1/fileName";
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path))
        .thenThrow(new InvalidArgumentsException(format("Not found file, path: %s", path)));

    assertThatThrownBy(()
                           -> fileStoreService.listFileStoreNodesOnPath(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, path, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(format("Not found file, path: %s", path));
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testListRootFolderNodes() {
    FolderNodeDTO folderNodeDTO =
        FolderNodeDTO.builder().identifier(ROOT_FOLDER_IDENTIFIER).name(ROOT_FOLDER_NAME).build();
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ROOT_FOLDER_IDENTIFIER))
        .thenReturn(Optional.of(createNgFileTypeFolder()));
    when(fileStoreRepository.findAllAndSort(
             createCriteriaByScopeAndParentIdentifier(
                 Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), ROOT_FOLDER_IDENTIFIER),
             createSortByLastModifiedAtDesc()))
        .thenReturn(getNgFiles());

    FolderNodeDTO populatedFolderNodeDTO =
        fileStoreService.listFolderNodes(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, folderNodeDTO, null);

    assertThat(populatedFolderNodeDTO).isNotNull();
    assertThat(populatedFolderNodeDTO.getName()).isEqualTo(ROOT_FOLDER_NAME);
    assertThat(populatedFolderNodeDTO.getIdentifier()).isEqualTo(ROOT_FOLDER_IDENTIFIER);
    assertThat(populatedFolderNodeDTO.getChildren().size()).isEqualTo(3);
    assertThat(populatedFolderNodeDTO.getChildren())
        .contains(FileNodeDTO.builder().name("fileName").identifier("fileIdentifier").build(),
            FolderNodeDTO.builder().name("folderName1").identifier("folderIdentifier1").build(),
            FolderNodeDTO.builder().name("folderName2").identifier("folderIdentifier2").build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFilesAndFolders() {
    FileFilterDTO fileFilterDTO =
        FileFilterDTO.builder().searchTerm("searchTerm").identifiers(Collections.emptyList()).build();
    final ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(fileStoreRepository.findAll(any(), any()))
        .thenReturn(new PageImpl<>(Lists.newArrayList(builder().name("filename1").build())));
    Page<FileDTO> pageResponse = fileStoreService.listFilesAndFolders(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, fileFilterDTO, PageRequest.of(0, 10));

    verify(fileStoreRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), any());

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getName()).isEqualTo("filename1");

    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertThat(criteria).isNotNull();
    assertThat(criteria.getCriteriaObject().toString())
        .isEqualTo(
            "Document{{accountIdentifier=accountIdentifier, orgIdentifier=orgIdentifier, projectIdentifier=projectIdentifier, $or=[Document{{name=searchTerm}}, Document{{identifier=searchTerm}}, Document{{tags.key=searchTerm}}, Document{{tags.value=searchTerm}}]}}");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testListFilesAndFoldersWithIdentifiers() {
    FileFilterDTO fileFilterDTO =
        FileFilterDTO.builder().searchTerm("searchTerm").identifiers(singletonList("ident1")).build();
    final ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(fileStoreRepository.findAll(any(), any()))
        .thenReturn(new PageImpl<>(Lists.newArrayList(builder().name("filename1").build())));
    Page<FileDTO> pageResponse = fileStoreService.listFilesAndFolders(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, fileFilterDTO, PageRequest.of(0, 10));

    verify(fileStoreRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), any());

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getName()).isEqualTo("filename1");

    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertThat(criteria).isNotNull();
    assertThat(criteria.getCriteriaObject().toString())
        .isEqualTo(
            "Document{{accountIdentifier=accountIdentifier, orgIdentifier=orgIdentifier, projectIdentifier=projectIdentifier, $or=[Document{{name=searchTerm}}, Document{{identifier=searchTerm}}, Document{{tags.key=searchTerm}}, Document{{tags.value=searchTerm}}], identifier=Document{{$in=[ident1]}}}}");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testListFilesAndFoldersWithoutFilter() {
    final ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    when(fileStoreRepository.findAll(any(), any()))
        .thenReturn(new PageImpl<>(Lists.newArrayList(builder().name("filename1").build())));
    Page<FileDTO> pageResponse = fileStoreService.listFilesAndFolders(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, PageRequest.of(0, 10));

    verify(fileStoreRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), any());

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getName()).isEqualTo("filename1");

    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertThat(criteria).isNotNull();
    assertThat(criteria.getCriteriaObject().toString())
        .isEqualTo(
            "Document{{accountIdentifier=accountIdentifier, orgIdentifier=orgIdentifier, projectIdentifier=projectIdentifier}}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFilesWithFoldersException() {
    FileFilterDTO fileFilterDTO =
        FileFilterDTO.builder().searchTerm("searchTerm").identifiers(Collections.emptyList()).build();
    assertThatThrownBy(()
                           -> fileStoreService.listFilesAndFolders(
                               null, ORG_IDENTIFIER, PROJECT_IDENTIFIER, fileFilterDTO, PageRequest.of(0, 10)))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Account identifier cannot be null or empty");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testListFilesWithoutFilterShouldReturnAll() {
    when(fileStoreRepository.findAllAndSort(
             createTestCriteriaForListFiles(null, ""), createSortByLastModifiedAtDesc(), PageRequest.of(0, 10)))
        .thenReturn(new PageImpl<>(Lists.newArrayList(builder().name("filename1").build())));
    Page<FileDTO> pageResponse = fileStoreService.listFilesWithFilter(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "", "", null, PageRequest.of(0, 10));

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getName()).isEqualTo("filename1");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testListFilesWithFilterShouldReturnAll() {
    FilesFilterPropertiesDTO filter = FilesFilterPropertiesDTO.builder()
                                          .fileUsage(FileUsage.CONFIG)
                                          .createdBy(new EmbeddedUserDetailsDTO("Name", "email@test.com"))
                                          .referencedBy(new ReferencedByDTO(EntityType.FILES, "test-name"))
                                          .build();

    filter.setTags(singletonMap("key", "value"));

    when(fileStoreRepository.findAllAndSort(createTestCriteriaForListFiles(filter, "test-search-term"),
             createSortByLastModifiedAtDesc(), PageRequest.of(0, 10)))
        .thenReturn(new PageImpl<>(Lists.newArrayList(builder().name("filename1").build())));
    Page<FileDTO> pageResponse = fileStoreService.listFilesWithFilter(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "", "test-search-term", filter, PageRequest.of(0, 10));

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent().size()).isEqualTo(1);
    assertThat(pageResponse.getContent().get(0).getName()).isEqualTo("filename1");
  }

  private Criteria createTestCriteriaForListFiles(FilesFilterPropertiesDTO filterProperties, String searchTerm) {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    return createFilesFilterCriteria(scope, filterProperties, searchTerm, Collections.emptyList());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testListFilesWithFilterException() {
    assertThatThrownBy(
        ()
            -> fileStoreService.listFilesWithFilter(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
                "filterIdentifier", "", new FilesFilterPropertiesDTO(), PageRequest.of(0, 10)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Can not apply both filter properties and saved filter together");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testListCreatedByShouldReturnAll() {
    when(fileStoreRepository.aggregate(any(), any()))
        .thenReturn(new AggregationResults<>(
            Lists.newArrayList(EmbeddedUser.builder().name("testuser1").email("testuser1@test.com").build(),
                EmbeddedUser.builder().name("testuser2").email("testuser2@test.com").build()),
            new Document()));
    Set<EmbeddedUserDetailsDTO> createdByList =
        fileStoreService.getCreatedByList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(createdByList).isNotNull();
    assertThat(createdByList.size()).isEqualTo(2);
    assertThat(createdByList.stream().findFirst().get().getName()).isEqualTo("testuser1");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithNullAccount() {
    assertThatThrownBy(
        () -> fileStoreService.getWithChildren(null, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER, true))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Account identifier cannot be null or empty");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithNullIdentifier() {
    assertThatThrownBy(
        () -> fileStoreService.getWithChildren(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, true))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("File or folder identifier cannot be null or empty");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithEmptyResult() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.empty());
    Optional<FileStoreNodeDTO> fileStoreNodeDTO =
        fileStoreService.getWithChildren(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER, true);

    assertThat(fileStoreNodeDTO).isNotNull();
    assertThat(fileStoreNodeDTO.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWitFileIncludingContent() {
    String fileContent = "file content";
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(createNgFileTypeFile()));

    when(fileStructureService.getFileContent(FILE_UUID)).thenReturn(fileContent);

    Optional<FileStoreNodeDTO> fileStoreNodeDTO =
        fileStoreService.getWithChildren(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER, true);

    assertThat(fileStoreNodeDTO).isNotNull();
    assertThat(fileStoreNodeDTO.isPresent()).isTrue();
    FileStoreNodeDTO storeNodeDTO = fileStoreNodeDTO.get();
    assertThat(storeNodeDTO.getType()).isEqualTo(NGFileType.FILE);

    FileNodeDTO fileNodeDTO = (FileNodeDTO) storeNodeDTO;
    assertThat(fileNodeDTO.getContent()).isEqualTo(fileContent);

    assertThat(fileNodeDTO.getDescription()).isEqualTo("oldDescription");
    assertThat(fileNodeDTO.getName()).isEqualTo("oldName");
    assertThat(fileNodeDTO.getIdentifier()).isEqualTo(FILE_IDENTIFIER);
    assertThat(fileNodeDTO.getFileUsage()).isEqualTo(FileUsage.MANIFEST_FILE);
    assertThat(fileNodeDTO.getMimeType()).isEqualTo(YML_MIME_TYPE);
    assertThat(fileNodeDTO.getParentIdentifier()).isEqualTo(PARENT_IDENTIFIER);
    assertThat(fileNodeDTO.getTags()).isEqualTo(getNgTags());
    assertThat(fileNodeDTO.getLastModifiedBy()).isEqualTo(getEmbeddedUserDetailsDTO());
    assertThat(fileNodeDTO.getLastModifiedAt()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWitFolderIncludingContent() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(createNgFileTypeFolder()));
    doNothing()
        .when(fileStructureService)
        .createFolderTreeStructure(
            any(FolderNodeDTO.class), eq(Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)), eq(true));

    Optional<FileStoreNodeDTO> fileStoreNodeDTO =
        fileStoreService.getWithChildren(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER, true);

    assertThat(fileStoreNodeDTO).isNotNull();
    assertThat(fileStoreNodeDTO.isPresent()).isTrue();
    FileStoreNodeDTO storeNodeDTO = fileStoreNodeDTO.get();
    assertThat(storeNodeDTO.getType()).isEqualTo(NGFileType.FOLDER);

    FolderNodeDTO folderNodeDTO = (FolderNodeDTO) storeNodeDTO;

    assertThat(folderNodeDTO.getName()).isEqualTo(FOLDER_NAME);
    assertThat(folderNodeDTO.getIdentifier()).isEqualTo(FOLDER_IDENTIFIER);
    assertThat(folderNodeDTO.getParentIdentifier()).isEqualTo(PARENT_IDENTIFIER);
    assertThat(folderNodeDTO.getLastModifiedBy()).isEqualTo(getEmbeddedUserDetailsDTO());
    assertThat(folderNodeDTO.getLastModifiedAt()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldNotSaveNgFileWithIdentifierRoot() {
    // Given
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);
    final FileDTO fileDto = aFileDto();
    fileDto.setIdentifier(ROOT_FOLDER_IDENTIFIER);
    fileDto.setDraft(false);
    // When
    assertThatThrownBy(() -> fileStoreService.create(fileDto, getStreamWithDummyContent()))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining(
            "Try another identifier, file with identifier [" + ROOT_FOLDER_IDENTIFIER + "] already exists.",
            fileDto.getIdentifier());
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldNotSaveNgFolderWithIdentifierRoot() {
    // Given
    givenThatFileNotExistInDB();
    final FileDTO fileDto = aFolderDto();
    fileDto.setIdentifier(ROOT_FOLDER_IDENTIFIER);
    fileDto.setDraft(false);
    // When
    assertThatThrownBy(() -> fileStoreService.create(fileDto, null))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining(
            "Try another identifier, folder with identifier [" + ROOT_FOLDER_IDENTIFIER + "] already exists.",
            fileDto.getIdentifier());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenIdentifierIsNull() {
    assertThatThrownBy(
        () -> fileStoreService.listReferencedBy(new SearchPageParams(), "account", "org", "proj", "", EntityType.FILES))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("File identifier cannot be empty");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenAccountIsNull() {
    assertThatThrownBy(
        () -> fileStoreService.listReferencedBy(new SearchPageParams(), "", "org", "proj", "ident", EntityType.FILES))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Account identifier cannot be null or empty");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldNotThrowExceptionWhenAllArgumentsAreNotEmpty() {
    givenThatFileExistsInDatabase();

    assertThatCode(()
                       -> fileStoreService.listReferencedBy(
                           new SearchPageParams(), "account", "org", "proj", "ident", EntityType.FILES))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteBatchFile() {
    NGFile ngFile = createNgFileTypeFile();
    List<String> fileIdentifiers = Collections.singletonList(FILE_IDENTIFIER);
    doNothing().when(fileService).deleteFile(any(), any(FileBucket.class));
    when(fileFailsafeService.deleteAndPublish(any(), anyBoolean())).thenReturn(true);
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_IDENTIFIER))
        .thenReturn(Optional.of(ngFile));

    final ArgumentCaptor<String> fileChunksIdCaptor = ArgumentCaptor.forClass(String.class);
    fileStoreService.deleteBatch(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, fileIdentifiers);

    verify(fileService, times(1)).deleteFile(fileChunksIdCaptor.capture(), any(FileBucket.class));
    verify(fileFailsafeService, times(1)).deleteAndPublish(ngFile, false);

    String fileChunksId = fileChunksIdCaptor.getValue();
    assertThat(fileChunksId).isEqualTo(FILE_UUID);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteBatchFolder() {
    NGFile ngFile = createNgFileTypeFolder();
    List<String> fileIdentifiers = Collections.singletonList(FOLDER_IDENTIFIER);
    when(fileFailsafeService.deleteAndPublish(any(), anyBoolean())).thenReturn(true);
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FOLDER_IDENTIFIER))
        .thenReturn(Optional.of(ngFile));

    fileStoreService.deleteBatch(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, fileIdentifiers);

    verify(fileFailsafeService, times(1)).deleteAndPublish(ngFile, false);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteBatchWithException() {
    List<String> fileIdentifiers = Collections.singletonList(FOLDER_IDENTIFIER);
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FOLDER_IDENTIFIER))
        .thenThrow(new InvalidRequestException("Error msg"));

    assertThatThrownBy(
        () -> fileStoreService.deleteBatch(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, fileIdentifiers))
        .hasMessage("Error msg")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFileWithRootParentProjectNotExist() {
    FileDTO fileDTO = aFileDto();
    fileDTO.setOrgIdentifier(ORG_IDENTIFIER);
    fileDTO.setProjectIdentifier(PROJECT_IDENTIFIER);
    fileDTO.setParentIdentifier(ROOT_FOLDER_IDENTIFIER);
    when(projectService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)).thenReturn(Optional.empty());
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);

    assertThatThrownBy(() -> fileStoreService.create(fileDTO, null))
        .hasMessage(
            "Project with identifier [projectIdentifier] does not exist, orgIdentifier: orgIdentifier, accountIdentifier: accountIdentifier")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFileWithRootParentOrgNotExist() {
    FileDTO fileDTO = aFileDto();
    fileDTO.setOrgIdentifier(ORG_IDENTIFIER);
    fileDTO.setParentIdentifier(ROOT_FOLDER_IDENTIFIER);
    when(organizationService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER)).thenReturn(Optional.empty());
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);

    assertThatThrownBy(() -> fileStoreService.create(fileDTO, null))
        .hasMessage("Org with identifier [orgIdentifier] does not exist, accountIdentifier: accountIdentifier")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFileWithRootParentAccountNotExist() {
    FileDTO fileDTO = aFileDto();
    fileDTO.setParentIdentifier(ROOT_FOLDER_IDENTIFIER);
    fileDTO.setAccountIdentifier(INVALID_ACCOUNT_IDENTIFIER);
    when(accountService.getAccount(INVALID_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(
            format("Account with identifier %s does not exist", INVALID_ACCOUNT_IDENTIFIER)));
    givenThatExistsParentFolderButNotFile(PARENT_IDENTIFIER, FILE_IDENTIFIER);

    assertThatThrownBy(() -> fileStoreService.create(fileDTO, null))
        .hasMessage(format("Account with identifier %s does not exist", INVALID_ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class);
  }

  private static FileDTO aFileDto() {
    return FileDTO.builder()
        .identifier(FILE_IDENTIFIER)
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .description("some description")
        .name("file-name")
        .type(NGFileType.FILE)
        .parentIdentifier(PARENT_IDENTIFIER)
        .path("/Root/file-name")
        .build();
  }

  private static FileDTO aFolderDto() {
    return FileDTO.builder()
        .identifier(FOLDER_IDENTIFIER)
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .description("some description")
        .name("folder-name")
        .type(NGFileType.FOLDER)
        .build();
  }

  private void givenThatFileExistsInDatabase() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(Optional.of(builder().build()));
  }

  private void givenThatDatabaseIsEmpty() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
  }

  private static InputStream getStreamWithDummyContent() {
    return new ByteArrayInputStream("File content".getBytes());
  }

  private FileDTO createFileDto() {
    return FileDTO.builder()
        .type(NGFileType.FILE)
        .identifier("identifier1")
        .name("updatedName")
        .description("updatedDescription")
        .fileUsage(FileUsage.MANIFEST_FILE)
        .build();
  }

  @NotNull
  private List<NGTag> getNgTags() {
    return Collections.singletonList(NGTag.builder().key("key").value("value").build());
  }

  private EmbeddedUserDetailsDTO getEmbeddedUserDetailsDTO() {
    return EmbeddedUserDetailsDTO.builder().name(ADMIN_USER_NAME).email(ADMIN_USER_EMAIL).build();
  }

  private NGFile createNgFileTypeFile() {
    return builder()
        .type(NGFileType.FILE)
        .name("oldName")
        .description("oldDescription")
        .identifier(FILE_IDENTIFIER)
        .fileUuid(FILE_UUID)
        .fileUsage(FileUsage.MANIFEST_FILE)
        .parentIdentifier(PARENT_IDENTIFIER)
        .mimeType(YML_MIME_TYPE)
        .tags(getNgTags())
        .lastUpdatedBy(EmbeddedUser.builder().name(ADMIN_USER_NAME).email(ADMIN_USER_EMAIL).build())
        .lastModifiedAt(1L)
        .build();
  }

  private NGFile createNgFileTypeFolder() {
    return builder()
        .type(NGFileType.FOLDER)
        .name(FOLDER_NAME)
        .identifier(FOLDER_IDENTIFIER)
        .parentIdentifier(PARENT_IDENTIFIER)
        .lastUpdatedBy(EmbeddedUser.builder().name(ADMIN_USER_NAME).email(ADMIN_USER_EMAIL).build())
        .lastModifiedAt(1L)
        .path("/some/dummy/path")
        .build();
  }

  private List<NGFile> getNgFiles() {
    return Arrays.asList(builder()
                             .type(NGFileType.FOLDER)
                             .name("folderName1")
                             .identifier("folderIdentifier1")
                             .parentIdentifier(FILE_IDENTIFIER)
                             .createdBy(EmbeddedUser.builder().name("testuser1").email("testuser1@test.com").build())
                             .path("/asd/dummy")
                             .build(),
        builder()
            .type(NGFileType.FOLDER)
            .name("folderName2")
            .identifier("folderIdentifier2")
            .parentIdentifier(FILE_IDENTIFIER)
            .createdBy(EmbeddedUser.builder().name("testuser1").email("testuser1@test.com").build())
            .path("/asd/dummy")
            .build(),
        builder()
            .type(NGFileType.FILE)
            .name("fileName")
            .identifier("fileIdentifier")
            .parentIdentifier(FILE_IDENTIFIER)
            .createdBy(EmbeddedUser.builder().name("testuser2").email("testuser2@test.com").build())
            .path("/asd/dummy")
            .build());
  }

  private List<NGFile> getRootNgFiles() {
    return Arrays.asList(builder()
                             .type(NGFileType.FOLDER)
                             .name("folder1")
                             .identifier("folder1")
                             .parentIdentifier(ROOT_FOLDER_IDENTIFIER)
                             .createdBy(EmbeddedUser.builder().name("testuser1").email("testuser1@test.com").build())
                             .path("/folder1")
                             .build());
  }

  private List<NGFile> getFolder1NgFiles() {
    return Arrays.asList(builder()
                             .type(NGFileType.FOLDER)
                             .name("folder2")
                             .identifier("folder2")
                             .parentIdentifier("folder1")
                             .createdBy(EmbeddedUser.builder().name("testuser1").email("testuser1@test.com").build())
                             .path("/folder1/folder2")
                             .build(),
        builder()
            .type(NGFileType.FILE)
            .name("fileName")
            .identifier("fileIdentifier")
            .parentIdentifier("folder1")
            .createdBy(EmbeddedUser.builder().name("testuser2").email("testuser2@test.com").build())
            .path("/folder1/fileName")
            .build());
  }

  private NGFile getNgFile() {
    return builder()
        .type(NGFileType.FILE)
        .name("fileName")
        .identifier("fileIdentifier")
        .parentIdentifier("folder1")
        .createdBy(EmbeddedUser.builder().name("testuser2").email("testuser2@test.com").build())
        .path("/folder1/fileName")
        .build();
  }

  private void givenThatFileNotExistInDB() {
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
             any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
  }

  private void givenThatExistsParentFolderButNotFile(String parentIdentifier, String fileIdentifier) {
    NGFile parentFolder = mock(NGFile.class);
    when(parentFolder.isFolder()).thenReturn(true);
    when(parentFolder.getPath()).thenReturn("/Root/folder");
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
             any(), any(), any(), eq(parentIdentifier), eq(fileIdentifier)))
        .thenReturn(Optional.empty());
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
             any(), any(), any(), eq(parentIdentifier), eq(null)))
        .thenReturn(Optional.of(parentFolder));
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), eq(fileIdentifier)))
        .thenReturn(Optional.empty());
    when(fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), eq(parentIdentifier)))
        .thenReturn(Optional.of(parentFolder));
  }
}
