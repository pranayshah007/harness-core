/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SearchPageParams;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.filestore.dto.FileFilterDTO;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.CDP)
public interface FileStoreService {
  /**
   * Create file.
   *
   * @param fileDto the file DTO object
   * @param content file content
   * @return created file DTO object
   */
  FileDTO create(@NotNull FileDTO fileDto, InputStream content);

  /**
   * Update file.
   *
   * @param fileDto the file DTO
   * @param content file content
   * @param identifier file identifier
   * @return updated file DTO
   */

  FileDTO update(@NotNull FileDTO fileDto, InputStream content, String identifier);

  /**
   * Get the file metadata with its content transformed to String if includeContent is set.
   * If identifier is related to folder then returns recursively all files metadata
   * with its content including files in folder sub-folders.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param identifier the file or folder identifier
   * @param includeContent include file content
   * @return file store node with content
   */
  Optional<FileStoreNodeDTO> get(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String identifier, boolean includeContent);

  /**
   * Download a file.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param fileIdentifier the file identifier
   * @return file
   */
  File downloadFile(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String fileIdentifier);

  /**
   * Delete file.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param identifier the file identifier
   * @return whether the file is successfully deleted
   */
  boolean delete(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier);

  /**
   * Get the list of folder nodes.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param folderNodeDTO the folder for which to return the list of nodes
   * @return the folder populated with nodes
   */
  FolderNodeDTO listFolderNodes(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull FolderNodeDTO folderNodeDTO);

  /**
   * Get list of entities file is referenced by.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param identifier the file identifier
   * @return list of entities file is referenced by
   */
  Page<EntitySetupUsageDTO> listReferencedBy(SearchPageParams pageParams, @NotNull String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @NotNull String identifier, EntityType entityType);

  /**
   *  List Files and Folders.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param fileFilterDTO filter Files based on search criteria
   * @param pageable the page request
   * @return the list of files and folders.
   */
  Page<FileDTO> listFilesAndFolders(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull FileFilterDTO fileFilterDTO, Pageable pageable);

  /**
   * List NG files by pages based on filter criteria.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param filterIdentifier the filter identifier
   * @param searchTerm the search term
   * @param filesFilterPropertiesDTO the filter properties
   * @param pageable the page
   * @return filtered list of NG files by pages
   */
  Page<FileDTO> listFilesWithFilter(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String filterIdentifier, String searchTerm, FilesFilterPropertiesDTO filesFilterPropertiesDTO, Pageable pageable);

  /**
   * Get the list of created by principals.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @return the list of created by principals.
   */
  Set<EmbeddedUserDetailsDTO> getCreatedByList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);

  /**
   * Get the list of supported entity types.
   *
   * @return supported entity types
   */
  List<EntityType> getSupportedEntityTypes();
}
