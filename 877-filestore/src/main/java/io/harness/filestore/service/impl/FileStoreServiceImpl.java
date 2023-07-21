/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service.impl;

import static io.harness.EntityType.PIPELINES;
import static io.harness.EntityType.PIPELINE_STEPS;
import static io.harness.EntityType.SECRETS;
import static io.harness.EntityType.SERVICE;
import static io.harness.EntityType.TEMPLATE;
import static io.harness.FileStoreConstants.ROOT_FOLDER_IDENTIFIER;
import static io.harness.FileStoreConstants.ROOT_FOLDER_NAME;
import static io.harness.FileStoreConstants.ROOT_FOLDER_PARENT_IDENTIFIER;
import static io.harness.FileStoreConstants.ROOT_FOLDER_PATH;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.FileBucket.FILE_STORE;
import static io.harness.exception.WingsException.USER;
import static io.harness.filestore.utils.FileStoreUtils.getSubPaths;
import static io.harness.filestore.utils.FileStoreUtils.isPathValid;
import static io.harness.filestore.utils.FileStoreUtils.nameChanged;
import static io.harness.filestore.utils.FileStoreUtils.parentChanged;
import static io.harness.filter.FilterType.FILESTORE;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createCreateByAggregation;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createCriteriaByScopeAndParentIdentifierAndNodesFilter;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createFilesAndFoldersFilterCriteria;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createFilesFilterCriteria;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createSortByLastModifiedAtDesc;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FileReference;
import io.harness.beans.Scope;
import io.harness.beans.SearchPageParams;
import io.harness.eraro.ErrorMessageConstants;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.file.beans.NGBaseFile;
import io.harness.filestore.FileStoreConfiguration;
import io.harness.filestore.dto.filter.FileStoreNodesFilterQueryPropertiesDTO;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.filestore.dto.mapper.EmbeddedUserDTOMapper;
import io.harness.filestore.dto.mapper.FileDTOMapper;
import io.harness.filestore.dto.mapper.FileStoreNodeDTOMapper;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.FileFailsafeService;
import io.harness.filestore.service.FileStoreService;
import io.harness.filestore.service.FileStructureService;
import io.harness.filestore.service.FileValidationService;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.filestore.dto.FileFilterDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.FileStoreRepository;
import io.harness.stream.BoundedInputStream;

import software.wings.service.intfc.FileService;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.serializer.HObjectMapper;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class FileStoreServiceImpl implements FileStoreService {
  private static final List<EntityType> SUPPORTED_ENTITY_TYPES =
      Lists.newArrayList(PIPELINES, PIPELINE_STEPS, SERVICE, SECRETS, TEMPLATE);
  private static final String PATH_SEPARATOR = "/";
  private final FileService fileService;
  private final FileStoreRepository fileStoreRepository;
  private final FileStoreConfiguration configuration;
  private final FileReferenceServiceImpl fileReferenceService;
  private final FilterService filterService;
  private final FileFailsafeService fileFailsafeService;
  private final FileStructureService fileStructureService;
  private final FileValidationService fileValidationService;
  private final NGSettingsClient settingsClient;

  @Override
  public FileDTO create(@NotNull FileDTO fileDto, InputStream content) {
    log.info("Creating {}: {}", fileDto.getType().name().toLowerCase(), fileDto);

    validateCreationFileDto(fileDto);
    fileDto.setPath(createPath(fileDto));

    NGFile ngFile = FileDTOMapper.getNGFileFromDTO(fileDto);

    if (shouldStoreFileContent(content, ngFile)) {
      log.info("Start creating file in file system, identifier: {}", fileDto.getIdentifier());
      saveFile(fileDto, ngFile, content);
    }

    return fileFailsafeService.saveAndPublish(ngFile);
  }

  @Override
  public FileDTO update(@NotNull FileDTO fileDto, InputStream content) {
    NGFile oldNGFile = findOrThrow(fileDto.getAccountIdentifier(), fileDto.getOrgIdentifier(),
        fileDto.getProjectIdentifier(), fileDto.getIdentifier());
    NGFile oldNGFileClone = (NGFile) HObjectMapper.clone(oldNGFile);

    validateUpdateFileDto(fileDto, oldNGFileClone);

    fileDto.setPath(createPath(fileDto));
    if (NGFileType.FOLDER.equals(oldNGFile.getType())) {
      updateChildrenPathsIfFolderRenamed(oldNGFile, fileDto);
    }

    NGFile updatedNGFile = FileDTOMapper.updateNGFile(fileDto, oldNGFile);
    if (shouldStoreFileContent(content, updatedNGFile)) {
      log.info("Start updating file in file system, identifier: {}", fileDto.getIdentifier());
      saveFile(fileDto, updatedNGFile, content);
    }

    return fileFailsafeService.updateAndPublish(oldNGFileClone, updatedNGFile);
  }

  @Override
  public Optional<FileDTO> get(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File identifier cannot be null or empty");
    }

    Optional<NGFile> ngFileOpt = find(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return ngFileOpt.map(FileDTOMapper::getFileDTOFromNGFile);
  }

  @Override
  public Optional<FileDTO> get(@NotNull FileDTO fileDTO) {
    Optional<NGFile> ngFileOpt = find(fileDTO.getAccountIdentifier(), fileDTO.getOrgIdentifier(),
        fileDTO.getProjectIdentifier(), fileDTO.getIdentifier());
    return ngFileOpt.map(FileDTOMapper::getFileDTOFromNGFile);
  }

  @Override
  public Optional<FileDTO> getByPath(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String path) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (isEmpty(path)) {
      throw new InvalidArgumentsException("File path cannot be null or empty");
    }

    Optional<NGFile> ngFileOpt = findByPath(accountIdentifier, orgIdentifier, projectIdentifier, path);
    return ngFileOpt.map(FileDTOMapper::getFileDTOFromNGFile);
  }

  @Override
  public Optional<FileStoreNodeDTO> getWithChildrenByPath(@NotNull final String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull final String path, boolean includeContent) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (isEmpty(path)) {
      throw new InvalidArgumentsException("File or folder path cannot be null or empty");
    }

    Optional<NGFile> ngFileOpt = findByPath(accountIdentifier, orgIdentifier, projectIdentifier, path);
    return ngFileOpt.map(NGFile::getIdentifier)
        .flatMap(identifier
            -> getWithChildren(accountIdentifier, orgIdentifier, projectIdentifier, identifier, includeContent));
  }

  @Override
  public Optional<FileStoreNodeDTO> getWithChildren(@NotNull final String accountIdentifier, final String orgIdentifier,
      final String projectIdentifier, @NotNull final String identifier, boolean includeContent) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File or folder identifier cannot be null or empty");
    }

    Optional<NGFile> ngFileOpt = find(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (!ngFileOpt.isPresent()) {
      return Optional.empty();
    }

    NGFile ngFile = ngFileOpt.get();
    if (ngFile.isFile()) {
      String fileContent = null;
      if (includeContent) {
        fileContent = fileStructureService.getFileContent(ngFile.getFileUuid());
      }
      return Optional.of(FileStoreNodeDTOMapper.getFileNodeDTO(ngFile, fileContent));
    }

    FolderNodeDTO folderNodeDTO = FileStoreNodeDTOMapper.getFolderNodeDTO(ngFile);
    fileStructureService.createFolderTreeStructure(
        folderNodeDTO, Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), includeContent);
    return Optional.of(folderNodeDTO);
  }

  @Override
  public String getFileContentAsString(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String scopedFilePath, long allowedBytesFileSize) {
    FileReference fileReference = FileReference.of(scopedFilePath, accountIdentifier, orgIdentifier, projectIdentifier);

    Optional<FileStoreNodeDTO> file = getWithChildrenByPath(fileReference.getAccountIdentifier(),
        fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), true);

    if (file.isEmpty()) {
      throw new InvalidRequestException(format("File not found in local file store, path [%s], scope: [%s]",
          fileReference.getPath(), fileReference.getScope()));
    }

    FileStoreNodeDTO fileStoreNodeDTO = file.get();
    if (!(fileStoreNodeDTO instanceof FileNodeDTO)) {
      throw new InvalidRequestException(
          format("Found folder reference instead of file reference, path [%s], scope: [%s]", fileReference.getPath(),
              fileReference.getScope()));
    }

    String content = ((FileNodeDTO) fileStoreNodeDTO).getContent();
    if (content.getBytes(StandardCharsets.UTF_8).length > allowedBytesFileSize) {
      throw new InvalidRequestException(format("Too large file, scopedFilePath: %s", scopedFilePath));
    }

    return content;
  }

  @Override
  public File downloadFile(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String fileIdentifier) {
    if (isEmpty(fileIdentifier)) {
      throw new InvalidArgumentsException("File identifier cannot be null or empty");
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }

    NGFile ngFile = findOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, fileIdentifier);
    if (ngFile.isFolder()) {
      throw new InvalidArgumentsException(
          format("Downloading folder not supported, fileIdentifier: %s", fileIdentifier));
    }

    File file = new File(Files.createTempDir(), ngFile.getName());
    log.info("Start downloading file, fileIdentifier: {}, filePath: {}", fileIdentifier, file.getPath());
    return fileService.download(ngFile.getFileUuid(), file, FILE_STORE);
  }

  @Override
  public boolean delete(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String identifier, boolean forceDelete) {
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File identifier cannot be empty");
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (ROOT_FOLDER_IDENTIFIER.equals(identifier)) {
      throw new InvalidArgumentsException(format("Root folder [%s] can not be deleted.", ROOT_FOLDER_IDENTIFIER));
    }
    if (forceDelete && !isForceDeleteFFEnabledViaSettings(accountIdentifier)) {
      throw new InvalidRequestException(ErrorMessageConstants.FORCE_DELETE_SETTING_NOT_ENABLED, USER);
    }

    NGFile file = findOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    if (!forceDelete) {
      fileReferenceService.validateReferenceByAndThrow(file);
    }

    return deleteFileOrFolder(file, forceDelete);
  }

  @Override
  public FolderNodeDTO listFolderNodes(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull FolderNodeDTO folderNodeDTO,
      @Nullable FileStoreNodesFilterQueryPropertiesDTO filterParams) {
    FolderNodeDTO updatedFolderNode = fixFolderNode(accountIdentifier, orgIdentifier, projectIdentifier, folderNodeDTO);
    return populateFolderNode(
        Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), updatedFolderNode, filterParams);
  }

  @Override
  public FolderNodeDTO listFileStoreNodesOnPath(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String path, @Nullable FileStoreNodesFilterQueryPropertiesDTO filterParams) {
    if (!isPathValid(path)) {
      throw new InvalidArgumentsException(format("Invalid file path, path: %s", path));
    }
    findByPath(accountIdentifier, orgIdentifier, projectIdentifier, path)
        .orElseThrow(
            ()
                -> new InvalidArgumentsException(format(
                    "Not found file/folder on path [%s], accountIdentifier [%s], orgIdentifier [%s] and projectIdentifier [%s]",
                    path, accountIdentifier, orgIdentifier, projectIdentifier)));

    List<String> subPaths = getSubPaths(path).orElseThrow(
        () -> new InvalidArgumentsException(format("Unable to extract sub-parts of path, path: %s", path)));
    FolderNodeDTO root = FolderNodeDTO.builder()
                             .path(ROOT_FOLDER_PATH)
                             .parentIdentifier(ROOT_FOLDER_PARENT_IDENTIFIER)
                             .identifier(ROOT_FOLDER_IDENTIFIER)
                             .name(ROOT_FOLDER_NAME)
                             .build();
    return populateFolderNodeIncludingSubNodesOnPath(
        Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), root, subPaths, filterParams);
  }

  @Override
  public Page<EntitySetupUsageDTO> listReferencedBy(SearchPageParams pageParams, @NotNull String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @NotNull String identifier, EntityType entityType) {
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File identifier cannot be empty");
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }

    NGFile file = findOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return fileReferenceService.getReferencedBy(pageParams, file, entityType);
  }

  @Override
  public Page<FileDTO> listFilesAndFolders(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull FileFilterDTO fileFilterDTO, Pageable pageable) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }

    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    Criteria criteria = createFilesAndFoldersFilterCriteria(scope, fileFilterDTO);

    Page<NGFile> ngFiles = fileStoreRepository.findAll(criteria, pageable);
    return new PageImpl<>(
        ngFiles.map(FileDTOMapper::getFileDTOFromNGFile).getContent(), pageable, ngFiles.getTotalElements());
  }

  @Override
  public Page<FileDTO> listFilesWithFilter(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String filterIdentifier, String searchTerm, FilesFilterPropertiesDTO filterProperties, Pageable pageable) {
    if (isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }

    if (isNotEmpty(filterIdentifier)) {
      FilterDTO filterDTO =
          filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FILESTORE);
      filterProperties = (FilesFilterPropertiesDTO) filterDTO.getFilterProperties();
    }

    List<String> fileIdentifiers = Collections.emptyList();
    if (filterProperties != null && filterProperties.getReferencedBy() != null
        && filterProperties.getReferencedBy().getType() != null) {
      fileIdentifiers = fileReferenceService.getAllFileIdentifiersReferencedByInScope(
          Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), filterProperties.getReferencedBy().getType(),
          filterProperties.getReferencedBy().getName());
    }

    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    Criteria criteria = createFilesFilterCriteria(scope, filterProperties, searchTerm, fileIdentifiers);

    Page<NGFile> ngFiles = fileStoreRepository.findAllAndSort(criteria, createSortByLastModifiedAtDesc(), pageable);
    List<FileDTO> fileDTOS = ngFiles.stream().map(FileDTOMapper::getFileDTOFromNGFile).collect(Collectors.toList());
    return new PageImpl<>(fileDTOS, pageable, ngFiles.getTotalElements());
  }

  @Override
  public Set<EmbeddedUserDetailsDTO> getCreatedByList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    Aggregation aggregation = createCreateByAggregation(scope);

    AggregationResults<EmbeddedUser> aggregate = fileStoreRepository.aggregate(aggregation, EmbeddedUser.class);

    return aggregate.getMappedResults()
        .stream()
        .filter(Objects::nonNull)
        .filter(EmbeddedUser::existNameAndEmail)
        .map(EmbeddedUserDTOMapper::fromEmbeddedUser)
        .collect(Collectors.toSet());
  }

  @Override
  public List<EntityType> getSupportedEntityTypes() {
    return SUPPORTED_ENTITY_TYPES;
  }

  @Override
  public void deleteBatch(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> identifiers) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (isEmpty(identifiers)) {
      return;
    }

    log.info("Start batch deleting files and folders, accountIdentifier: {}, orgIdentifier: {}, projectIdentifier: {}",
        accountIdentifier, orgIdentifier, projectIdentifier);
    identifiers.forEach(identifier -> {
      NGFile ngFile = findOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      if (ngFile.isFile()) {
        deleteFile(ngFile, false);
      } else {
        // folder
        fileFailsafeService.deleteAndPublish(ngFile, false);
      }
    });
  }

  private String getDuplicateEntityIdentifierMessage(@NotNull FileDTO fileDto) {
    return format("Try another identifier, %s with identifier [%s] already exists.",
        fileDto.getType().name().toLowerCase(), fileDto.getIdentifier());
  }

  private String getDuplicateEntityNameMessage(@NotNull FileDTO fileDto) {
    return format("Try another name, %s with name [%s] already exists in the parent folder [%s].",
        fileDto.getType().name().toLowerCase(), fileDto.getName(), fileDto.getParentIdentifier());
  }

  private boolean shouldStoreFileContent(InputStream content, NGFile ngFile) {
    return content != null && !ngFile.isDraft() && ngFile.isFile();
  }

  private NGFile findOrThrow(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier)
        .orElseThrow(
            ()
                -> new InvalidArgumentsException(format(
                    "Not found file/folder with identifier [%s], accountIdentifier [%s], orgIdentifier [%s] and projectIdentifier [%s]",
                    identifier, accountIdentifier, orgIdentifier, projectIdentifier)));
  }

  private Optional<NGFile> find(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  private Optional<NGFile> findByPath(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String path) {
    return fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPath(
        accountIdentifier, orgIdentifier, projectIdentifier, path);
  }

  private void saveFile(FileDTO fileDto, NGFile ngFile, @NotNull InputStream content) {
    BoundedInputStream fileContent =
        new BoundedInputStream(content, configuration.getFileUploadLimits().getFileStoreFileLimit());
    NGBaseFile ngBaseFile = FileDTOMapper.getNgBaseFileFromFileDTO(fileDto);
    fileService.saveFile(ngBaseFile, fileContent, FILE_STORE);
    ngFile.setSize(fileContent.getTotalBytesRead());
    ngFile.setFileUuid(ngBaseFile.getFileUuid());
    ngFile.setChecksumType(ngBaseFile.getChecksumType());
    ngFile.setChecksum(ngBaseFile.getChecksum());
  }

  private void updateChildrenPathsIfFolderRenamed(NGFile oldNGFile, FileDTO fileDto) {
    if (parentChanged(oldNGFile, fileDto) || nameChanged(oldNGFile, fileDto)) {
      List<NGFile> ngFiles = fileStructureService.listFolderChildrenByPath(oldNGFile);
      String oldParentPath = oldNGFile.getPath();
      String newParentPath = fileDto.getPath();
      ngFiles.forEach(file -> {
        String newPath = file.getPath().replace(oldParentPath, newParentPath);
        file.setPath(newPath);
        fileStoreRepository.save(file);
      });
    }
  }

  // in the case when we need to return the whole folder structure, create recursion on this method
  private FolderNodeDTO populateFolderNode(
      Scope scope, FolderNodeDTO folderNode, FileStoreNodesFilterQueryPropertiesDTO filterParams) {
    List<FileStoreNodeDTO> fileStoreNodes = listFolderChildren(scope, folderNode.getIdentifier(), filterParams);
    for (FileStoreNodeDTO node : fileStoreNodes) {
      folderNode.addChild(node);
    }
    return folderNode;
  }

  private List<FileStoreNodeDTO> listFolderChildren(
      Scope scope, String folderIdentifier, FileStoreNodesFilterQueryPropertiesDTO filterParams) {
    return listAllByParentIdentifierFilteredByParamsAndSortedByLastModifiedAt(scope, folderIdentifier, filterParams)
        .stream()
        .filter(Objects::nonNull)
        .map(ngFile
            -> ngFile.isFolder() ? FileStoreNodeDTOMapper.getFolderNodeDTO(ngFile)
                                 : FileStoreNodeDTOMapper.getFileNodeDTO(ngFile, null))
        .collect(Collectors.toList());
  }

  private FolderNodeDTO populateFolderNodeIncludingSubNodesOnPath(Scope scope, FolderNodeDTO folderNode,
      final List<String> subNodePaths, FileStoreNodesFilterQueryPropertiesDTO filterParams) {
    List<FileStoreNodeDTO> fileStoreNodes =
        listFolderChildrenIncludingSubNodesOnPath(scope, folderNode.getIdentifier(), subNodePaths, filterParams);
    for (FileStoreNodeDTO node : fileStoreNodes) {
      folderNode.addChild(node);
    }
    return folderNode;
  }

  private List<FileStoreNodeDTO> listFolderChildrenIncludingSubNodesOnPath(Scope scope, String folderIdentifier,
      @NotNull final List<String> subNodePaths, FileStoreNodesFilterQueryPropertiesDTO filterParams) {
    return listAllByParentIdentifierFilteredByParamsAndSortedByLastModifiedAt(scope, folderIdentifier, filterParams)
        .stream()
        .filter(Objects::nonNull)
        .map(ngFile -> {
          if (ngFile.isFolder()) {
            FolderNodeDTO folderNode = FileStoreNodeDTOMapper.getFolderNodeDTO(ngFile);
            if (subNodePaths.contains(ngFile.getPath())) {
              subNodePaths.remove(ngFile.getPath());
              populateFolderNodeIncludingSubNodesOnPath(scope, folderNode, subNodePaths, filterParams);
            }
            return folderNode;
          } else {
            return FileStoreNodeDTOMapper.getFileNodeDTO(ngFile, null);
          }
        })
        .collect(Collectors.toList());
  }

  private List<NGFile> listAllByParentIdentifierFilteredByParamsAndSortedByLastModifiedAt(
      Scope scope, String parentIdentifier, FileStoreNodesFilterQueryPropertiesDTO filterParams) {
    return fileStoreRepository.findAllAndSort(
        createCriteriaByScopeAndParentIdentifierAndNodesFilter(scope, parentIdentifier, filterParams),
        createSortByLastModifiedAtDesc());
  }

  private FolderNodeDTO fixFolderNode(final String accountIdentifier, final String orgIdentifier,
      final String projectIdentifier, FolderNodeDTO folderNodeDTO) {
    final String folderIdentifier = folderNodeDTO.getIdentifier();
    final String folderName = folderNodeDTO.getName();
    if (ROOT_FOLDER_IDENTIFIER.equals(folderIdentifier) || ROOT_FOLDER_NAME.equals(folderName)) {
      return FileStoreNodeDTOMapper.getFolderNodeDTO(
          folderNodeDTO, ROOT_FOLDER_PARENT_IDENTIFIER, ROOT_FOLDER_NAME, ROOT_FOLDER_PATH);
    }

    NGFile ngFile = findOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, folderIdentifier);
    if (ngFile.isFile()) {
      throw new InvalidArgumentsException(format(
          "Required folder, found file with identifier [%s], accountIdentifier [%s], orgIdentifier [%s] and projectIdentifier [%s]",
          folderIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    }

    return FileStoreNodeDTOMapper.getFolderNodeDTO(ngFile);
  }

  private boolean deleteFileOrFolder(NGFile fileOrFolder, boolean forceDelete) {
    if (NGFileType.FOLDER.equals(fileOrFolder.getType())) {
      return deleteFolder(fileOrFolder, forceDelete);
    } else {
      return deleteFile(fileOrFolder, forceDelete);
    }
  }

  private boolean deleteFolder(NGFile folder, boolean forceDelete) {
    List<NGFile> childrenFiles = listFilesByParent(folder);
    if (!isEmpty(childrenFiles)) {
      childrenFiles.stream()
          .filter(Objects::nonNull)
          .forEach(fileOrFolder -> deleteFileOrFolder(fileOrFolder, forceDelete));
    }

    return fileFailsafeService.deleteAndPublish(folder, forceDelete);
  }

  private List<NGFile> listFilesByParent(NGFile parent) {
    return fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
        parent.getAccountIdentifier(), parent.getOrgIdentifier(), parent.getProjectIdentifier(),
        parent.getIdentifier());
  }

  private boolean deleteFile(NGFile file, boolean forceDelete) {
    try {
      fileService.deleteFile(file.getFileUuid(), FILE_STORE);
    } catch (Exception e) {
      log.error(
          "Failed to delete file store chunks id: {}, accountIdentifier: {}, orgIdentifier: {}, projectIdentifier: {}, identifier: {}",
          file.getFileUuid(), file.getAccountIdentifier(), file.getOrgIdentifier(), file.getProjectIdentifier(),
          file.getIdentifier(), e);
      throw new InvalidRequestException(format(
          "Failed to delete file store chunks id: %s, file identifier: %s", file.getFileUuid(), file.getIdentifier()));
    }

    return fileFailsafeService.deleteAndPublish(file, forceDelete);
  }

  private void validateCreationFileDto(FileDTO fileDto) {
    if (fileValidationService.isFileExistByName(fileDto)) {
      throw new DuplicateFieldException(getDuplicateEntityNameMessage(fileDto));
    }

    if (ROOT_FOLDER_IDENTIFIER.equals(fileDto.getIdentifier())
        || fileValidationService.isFileExistsByIdentifier(fileDto)) {
      throw new DuplicateFieldException(getDuplicateEntityIdentifierMessage(fileDto));
    }

    if (isEmpty(fileDto.getParentIdentifier())) {
      throw new InvalidArgumentsException("Parent folder identifier is mandatory.");
    }

    validateFileDto(fileDto);
  }

  private void validateUpdateFileDto(FileDTO fileDto, NGFile oldNGFile) {
    String identifier = fileDto.getIdentifier();
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File or folder identifier cannot be empty");
    }

    if (identifier.equals(fileDto.getParentIdentifier())) {
      throw new InvalidArgumentsException(
          format("File or folder identifier [%s] cannot be the same as parent folder identifier [%s]", identifier,
              fileDto.getParentIdentifier()));
    }

    validateFileDto(fileDto);

    // file usage is mandatory after first saving/updating to DB and not allowed to be updated
    if (oldNGFile.getFileUsage() != null) {
      if (fileDto.getFileUsage() == null) {
        throw new InvalidArgumentsException(
            format("File usage is required for already set usage, identifier [%s]", fileDto.getIdentifier()));
      }

      if (fileDto.getFileUsage() != null && oldNGFile.getFileUsage() != fileDto.getFileUsage()) {
        throw new InvalidArgumentsException(
            format("File usage cannot be updated, identifier [%s]", fileDto.getIdentifier()));
      }
    }
  }
  // common validation rules for creation and update
  private void validateFileDto(FileDTO fileDto) {
    if (!fileValidationService.parentFolderExists(fileDto)) {
      throw new InvalidArgumentsException(
          format("Parent folder with identifier [%s] does not exist", fileDto.getParentIdentifier()));
    }

    if (fileDto.getFileUsage() != null && fileDto.isFolder()) {
      throw new InvalidArgumentsException(
          format("File usage cannot be set for folder, identifier [%s]", fileDto.getIdentifier()));
    }
  }

  private String createPath(FileDTO fileDto) {
    String parentIdentifier = fileDto.getParentIdentifier();
    String name = fileDto.getName();

    if (ROOT_FOLDER_IDENTIFIER.equals(parentIdentifier)) {
      return format("%s%s", ROOT_FOLDER_PATH, name);
    }

    Optional<NGFile> parent =
        fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            fileDto.getAccountIdentifier(), fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(),
            parentIdentifier);

    if (!parent.isPresent()) {
      throw new InvalidArgumentsException(
          format("Parent folder with identifier [%s] does not exist", parentIdentifier));
    }

    if (isBlank(parent.get().getPath())) {
      throw new InvalidArgumentsException(
          format("Parent folder with identifier [%s] contains empty path", parentIdentifier));
    }

    return format("%s%s%s", parent.get().getPath(), PATH_SEPARATOR, name);
  }

  private boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }
}
