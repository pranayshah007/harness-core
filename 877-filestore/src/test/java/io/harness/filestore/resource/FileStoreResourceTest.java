/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.resource;

import static io.harness.EntityType.PIPELINES;
import static io.harness.EntityType.PIPELINE_STEPS;
import static io.harness.EntityType.SECRETS;
import static io.harness.EntityType.SERVICE;
import static io.harness.EntityType.TEMPLATE;
import static io.harness.exception.WingsException.USER;
import static io.harness.filestore.FilePermissionConstants.FILE_DELETE_PERMISSION;
import static io.harness.filestore.FilePermissionConstants.FILE_EDIT_PERMISSION;
import static io.harness.filestore.FilePermissionConstants.FILE_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VLAD;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SearchPageParams;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.impl.FileStoreServiceImpl;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.filestore.dto.FileFilterDTO;
import io.harness.ng.core.filestore.dto.FileStoreRequest;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileStoreResourceTest extends CategoryTest {
  private final String ACCOUNT = "account";
  private final String ORG = "org";
  private final String PROJECT = "project";
  private final String IDENTIFIER = "testFile";
  private final String EMPTY_TAGS = "";

  @Mock private FileStoreServiceImpl fileStoreService;
  @Mock private AccessControlClient accessControlClient;

  private FileStoreResource fileStoreResource;

  @Before
  public void setup() {
    fileStoreResource = new FileStoreResource(fileStoreService, accessControlClient);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDeleteFile() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_DELETE_PERMISSION));
    fileStoreResource.delete(ACCOUNT, ORG, PROJECT, IDENTIFIER);
    verify(fileStoreService).delete(ACCOUNT, ORG, PROJECT, IDENTIFIER);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void shouldDeleteFileWith128CharsIdentifier() {
    String identifier = RandomStringUtils.randomAlphanumeric(128);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_DELETE_PERMISSION));
    fileStoreResource.delete(ACCOUNT, ORG, PROJECT, identifier);
    verify(fileStoreService).delete(ACCOUNT, ORG, PROJECT, identifier);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteWithAccessDeniedException() {
    doThrow(new NGAccessDeniedException("Principal doesn't have file delete permission", USER, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(FILE_DELETE_PERMISSION));
    assertThatThrownBy(() -> fileStoreResource.delete(ACCOUNT, ORG, PROJECT, IDENTIFIER))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Principal doesn't have file delete permission");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFile() {
    File file = new File("returnedFile-download-path");
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.downloadFile(ACCOUNT, ORG, PROJECT, IDENTIFIER)).thenReturn(file);
    Response response = fileStoreResource.downloadFile(IDENTIFIER, ACCOUNT, ORG, PROJECT);
    File returnedFile = (File) response.getEntity();

    assertThat(returnedFile).isNotNull();
    assertThat(returnedFile.getPath()).isEqualTo(file.getPath());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFileWith128CharsIdentifier() {
    String identifier = RandomStringUtils.randomAlphanumeric(128);
    File file = new File("returnedFile-download-path");
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.downloadFile(ACCOUNT, ORG, PROJECT, identifier)).thenReturn(file);
    Response response = fileStoreResource.downloadFile(identifier, ACCOUNT, ORG, PROJECT);
    File returnedFile = (File) response.getEntity();

    assertThat(returnedFile).isNotNull();
    assertThat(returnedFile.getPath()).isEqualTo(file.getPath());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testCreateFile() {
    final FileDTO createRequest = FileDTO.builder()
                                      .parentIdentifier("Root")
                                      .identifier("testfile")
                                      .name("Test File")
                                      .type(NGFileType.FILE)
                                      .fileUsage(FileUsage.CONFIG)
                                      .build();

    ResponseDTO<FileDTO> response =
        fileStoreResource.create(ACCOUNT, ORG, PROJECT, EMPTY_TAGS, getStreamWithDummyContent(), createRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    verify(fileStoreService).create(any(), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFileWith128CharsIdentifier() {
    String identifier = RandomStringUtils.random(128, true, false);
    final FileDTO createRequest = FileDTO.builder()
                                      .parentIdentifier("Root")
                                      .identifier(identifier)
                                      .name("Test File")
                                      .type(NGFileType.FILE)
                                      .fileUsage(FileUsage.CONFIG)
                                      .build();

    ResponseDTO<FileDTO> response =
        fileStoreResource.create(ACCOUNT, ORG, PROJECT, EMPTY_TAGS, getStreamWithDummyContent(), createRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    verify(fileStoreService).create(any(), any());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testCreateFileViaYaml() {
    final FileDTO createRequest = FileDTO.builder()
                                      .parentIdentifier("Root")
                                      .identifier("testfile")
                                      .name("Test File")
                                      .type(NGFileType.FILE)
                                      .fileUsage(FileUsage.CONFIG)
                                      .build();

    ResponseDTO<FileDTO> response =
        fileStoreResource.createViaYaml(ACCOUNT, ORG, PROJECT, FileStoreRequest.builder().file(createRequest).build());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    verify(fileStoreService).create(any(), isNull());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testUpdateFile() {
    final FileDTO updateRequest = FileDTO.builder()
                                      .parentIdentifier("Root")
                                      .identifier("testfile")
                                      .name("Test File")
                                      .type(NGFileType.FILE)
                                      .fileUsage(FileUsage.CONFIG)
                                      .build();

    ResponseDTO<FileDTO> response = fileStoreResource.update(
        ACCOUNT, ORG, PROJECT, IDENTIFIER, EMPTY_TAGS, updateRequest, getStreamWithDummyContent());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    verify(fileStoreService).update(any(), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateFileWith128CharsIdentifier() {
    String identifier = RandomStringUtils.randomAlphanumeric(128);
    final FileDTO updateRequest = FileDTO.builder()
                                      .parentIdentifier("Root")
                                      .identifier(identifier)
                                      .name("Test File")
                                      .type(NGFileType.FILE)
                                      .fileUsage(FileUsage.CONFIG)
                                      .build();

    ResponseDTO<FileDTO> response = fileStoreResource.update(
        ACCOUNT, ORG, PROJECT, IDENTIFIER, EMPTY_TAGS, updateRequest, getStreamWithDummyContent());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    verify(fileStoreService).update(any(), any());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testUpdateFileViaYaml() {
    final FileDTO updateRequest = FileDTO.builder()
                                      .parentIdentifier("Root")
                                      .identifier("testfile")
                                      .name("Test File")
                                      .type(NGFileType.FILE)
                                      .fileUsage(FileUsage.CONFIG)
                                      .build();

    ResponseDTO<FileDTO> response = fileStoreResource.updateViaYaml(
        ACCOUNT, ORG, PROJECT, IDENTIFIER, FileStoreRequest.builder().file(updateRequest).build());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    verify(fileStoreService).update(any(), any());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testList() {
    final PageRequest pageRequest = PageRequest.builder().pageSize(10).pageIndex(1).build();

    ResponseDTO<Page<FileDTO>> response =
        fileStoreResource.list(ACCOUNT, ORG, PROJECT, singletonList(IDENTIFIER), "search-term", pageRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    ArgumentCaptor<FileFilterDTO> captor = ArgumentCaptor.forClass(FileFilterDTO.class);
    verify(fileStoreService).listFilesAndFolders(eq(ACCOUNT), eq(ORG), eq(PROJECT), captor.capture(), any());

    assertThat(captor.getValue())
        .extracting(FileFilterDTO::getIdentifiers, FileFilterDTO::getSearchTerm)
        .containsExactly(singletonList(IDENTIFIER), "search-term");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFileWithAccessDeniedException() {
    doThrow(new NGAccessDeniedException("Principal doesn't have file view permission", USER, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    assertThatThrownBy(() -> fileStoreResource.downloadFile(ACCOUNT, ORG, PROJECT, IDENTIFIER))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Principal doesn't have file view permission");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFileWithException() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.downloadFile(ACCOUNT, ORG, PROJECT, IDENTIFIER))
        .thenThrow(new InvalidRequestException("Unable to download file"));

    assertThatThrownBy(() -> fileStoreResource.downloadFile(IDENTIFIER, ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to download file");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFolderNodes() {
    FolderNodeDTO folderDTO = FolderNodeDTO.builder().build();
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.listFolderNodes(ACCOUNT, ORG, PROJECT, folderDTO, null))
        .thenReturn(FolderNodeDTO.builder().name("returnedFolderName").identifier("returnedFolderIdentifier").build());
    ResponseDTO<FolderNodeDTO> folderNodeDTOResponseDTO =
        fileStoreResource.listFolderNodes(ACCOUNT, ORG, PROJECT, folderDTO, null);
    FolderNodeDTO data = folderNodeDTOResponseDTO.getData();

    assertThat(data).isNotNull();
    assertThat(data.getName()).isEqualTo("returnedFolderName");
    assertThat(data.getIdentifier()).isEqualTo("returnedFolderIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFolderNodesWithAccessDeniedException() {
    FolderNodeDTO folderDTO = FolderNodeDTO.builder().build();
    doThrow(new NGAccessDeniedException("Principal doesn't have file view permission", USER, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    assertThatThrownBy(() -> fileStoreResource.listFolderNodes(ACCOUNT, ORG, PROJECT, folderDTO, null))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Principal doesn't have file view permission");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFolderNodesWithException() {
    FolderNodeDTO folderDTO = FolderNodeDTO.builder().build();
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.listFolderNodes(ACCOUNT, ORG, PROJECT, folderDTO, null))
        .thenThrow(new InvalidRequestException("Unable to list folder nodes"));

    assertThatThrownBy(() -> fileStoreResource.listFolderNodes(ACCOUNT, ORG, PROJECT, folderDTO, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to list folder nodes");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFileStoreNodesOnPath() {
    String path = "/folder1/folder2";
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.listFileStoreNodesOnPath(ACCOUNT, ORG, PROJECT, path, null))
        .thenReturn(FolderNodeDTO.builder().name("returnedFolderName").identifier("returnedFolderIdentifier").build());

    ResponseDTO<FolderNodeDTO> folderNodeDTOResponseDTO =
        fileStoreResource.listFileStoreNodesOnPath(ACCOUNT, ORG, PROJECT, path, null);

    FolderNodeDTO data = folderNodeDTOResponseDTO.getData();
    assertThat(data).isNotNull();
    assertThat(data.getName()).isEqualTo("returnedFolderName");
    assertThat(data.getIdentifier()).isEqualTo("returnedFolderIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFileStoreNodesOnPathWithAccessDeniedException() {
    String path = "/folder1/folder2";
    doThrow(new NGAccessDeniedException("Principal doesn't have file view permission", USER, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));

    assertThatThrownBy(() -> fileStoreResource.listFileStoreNodesOnPath(ACCOUNT, ORG, PROJECT, path, null))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Principal doesn't have file view permission");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFileStoreNodesOnPathWithException() {
    String path = "/folder1/folder2";
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.listFileStoreNodesOnPath(ACCOUNT, ORG, PROJECT, path, null))
        .thenThrow(new InvalidRequestException("Unable to list folder nodes including sub-nodes on path"));

    assertThatThrownBy(() -> fileStoreResource.listFileStoreNodesOnPath(ACCOUNT, ORG, PROJECT, path, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to list folder nodes including sub-nodes on path");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldListReferencedBy() {
    EntitySetupUsageDTO entitySetupUsage = EntitySetupUsageDTO.builder().build();
    int page = 1;
    int size = 10;
    SearchPageParams pageParams = SearchPageParams.builder().page(page).size(size).build();
    final Page<EntitySetupUsageDTO> entityServiceUsageList = new PageImpl<>(singletonList(entitySetupUsage));
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_EDIT_PERMISSION));
    when(fileStoreService.listReferencedBy(pageParams, ACCOUNT, ORG, PROJECT, IDENTIFIER, EntityType.PIPELINES))
        .thenReturn(entityServiceUsageList);
    ResponseDTO<PageResponse<EntitySetupUsageDTO>> response =
        fileStoreResource.getReferencedBy(page, size, ACCOUNT, ORG, PROJECT, IDENTIFIER, EntityType.PIPELINES, null);

    verify(fileStoreService).listReferencedBy(pageParams, ACCOUNT, ORG, PROJECT, IDENTIFIER, EntityType.PIPELINES);
    assertThat(response).isNotNull();
    assertThat(response.getData().getContent()).containsExactly(entitySetupUsage);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListReferencedByWithAccessDeniedException() {
    int page = 1;
    int size = 10;
    doThrow(new NGAccessDeniedException("Principal doesn't have file access permission", USER, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    assertThatThrownBy(()
                           -> fileStoreResource.getReferencedBy(
                               page, size, ACCOUNT, ORG, PROJECT, IDENTIFIER, EntityType.PIPELINES, null))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Principal doesn't have file access permission");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testListFilesWithFilter() {
    ArrayList<FileDTO> fileDTOS = Lists.newArrayList();
    fileDTOS.add(FileDTO.builder().name("file1").build());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.listFilesWithFilter(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(fileDTOS));

    ResponseDTO<Page<FileDTO>> response = fileStoreResource.listFilesWithFilter(
        PageRequest.builder().pageSize(1).pageIndex(0).build(), ACCOUNT, ORG, PROJECT, "", "", null);
    Page<FileDTO> pageResponse = response.getData();

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.isEmpty()).isFalse();
    assertThat(pageResponse.getContent().get(0).getName()).isEqualTo("file1");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testGetReferencedByTypes() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.getSupportedEntityTypes())
        .thenReturn(Lists.newArrayList(PIPELINES, PIPELINE_STEPS, SERVICE, SECRETS, TEMPLATE));
    ResponseDTO<List<EntityType>> response = fileStoreResource.getSupportedEntityTypes(ACCOUNT);
    List<EntityType> pageResponse = response.getData();

    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.isEmpty()).isFalse();
    assertThat(pageResponse.size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testListFilesWithFilterException() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    when(fileStoreService.listFilesWithFilter(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new InvalidRequestException("Can not apply both filter properties and saved filter together"));

    assertThatThrownBy(
        ()
            -> fileStoreResource.listFilesWithFilter(PageRequest.builder().pageSize(1).pageIndex(0).build(), ACCOUNT,
                ORG, PROJECT, "filterIdentifier", "", new FilesFilterPropertiesDTO()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Can not apply both filter properties and saved filter together");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListFilesWithAccessDeniedException() {
    doThrow(new NGAccessDeniedException("Principal doesn't have file view permission", USER, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    assertThatThrownBy(
        ()
            -> fileStoreResource.listFilesWithFilter(PageRequest.builder().pageSize(1).pageIndex(0).build(), ACCOUNT,
                ORG, PROJECT, "filterIdentifier", "", new FilesFilterPropertiesDTO()))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Principal doesn't have file view permission");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testListCreatedByUserNames() {
    when(fileStoreService.getCreatedByList(any(), any(), any()))
        .thenReturn(Sets.newHashSet(new EmbeddedUserDetailsDTO("test", "test@test.com"),
            new EmbeddedUserDetailsDTO("test1", "test1@test.com")));

    ResponseDTO<Set<EmbeddedUserDetailsDTO>> response = fileStoreResource.getCreatedByList(ACCOUNT, ORG, PROJECT);
    Set<EmbeddedUserDetailsDTO> returnedList = response.getData();

    assertThat(returnedList).isNotNull();
    assertThat(returnedList.isEmpty()).isFalse();
    assertThat(returnedList.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListCreatedByUserNamesWithAccessDeniedException() {
    doThrow(new NGAccessDeniedException("Principal doesn't have file view permission", USER, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(FILE_VIEW_PERMISSION));
    assertThatThrownBy(() -> fileStoreResource.getCreatedByList(ACCOUNT, ORG, PROJECT))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Principal doesn't have file view permission");
  }

  private static InputStream getStreamWithDummyContent() {
    return new ByteArrayInputStream("File content".getBytes());
  }
}
