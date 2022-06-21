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
import static io.harness.filestore.entities.NGFile.NGFiles;
import static io.harness.filestore.utils.FileStoreUtils.nameChanged;
import static io.harness.filestore.utils.FileStoreUtils.parentChanged;
import static io.harness.filter.FilterType.FILESTORE;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createCriteriaByScopeAndParentIdentifier;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createFilesAndFoldersFilterCriteria;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createFilesFilterCriteria;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createScopeCriteria;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createSortByLastModifiedAtDesc;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.Scope;
import io.harness.beans.SearchPageParams;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.file.beans.NGBaseFile;
import io.harness.filestore.FileStoreConfiguration;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.filestore.dto.mapper.EmbeddedUserDTOMapper;
import io.harness.filestore.dto.mapper.FileDTOMapper;
import io.harness.filestore.dto.mapper.FileStoreNodeDTOMapper;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.FileFailsafeService;
import io.harness.filestore.service.FileStoreService;
import io.harness.filestore.service.FileStructureService;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.filestore.dto.FileFilterDTO;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
  public FileDTO update(@NotNull FileDTO fileDto, InputStream content, @NotNull String identifier) {
    NGFile oldNGFile = fetchFileOrThrow(
        fileDto.getAccountIdentifier(), fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(), identifier);
    NGFile oldNGFileClone = (NGFile) HObjectMapper.clone(oldNGFile);

    validateUpdateFileDto(fileDto, identifier);

    fileDto.setPath(createPath(fileDto));
    if (NGFileType.FOLDER.equals(oldNGFile.getType())) {
      updateChildrenPathsIfFolderRenamed(oldNGFile, fileDto);
    }

    NGFile updatedNGFile = FileDTOMapper.updateNGFile(fileDto, oldNGFile);
    if (shouldStoreFileContent(content, updatedNGFile)) {
      log.info("Start updating file in file system, identifier: {}", identifier);
      saveFile(fileDto, updatedNGFile, content);
    }

    return fileFailsafeService.updateAndPublish(oldNGFileClone, updatedNGFile);
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

  @Override
  public Optional<FileStoreNodeDTO> get(@NotNull final String accountIdentifier, final String orgIdentifier,
      final String projectIdentifier, @NotNull final String identifier, boolean includeContent) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File or folder identifier cannot be null or empty");
    }

    Optional<NGFile> ngFileOpt =
        fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);

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
  public File downloadFile(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String fileIdentifier) {
    if (isEmpty(fileIdentifier)) {
      throw new InvalidArgumentsException("File identifier cannot be null or empty");
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }

    NGFile ngFile = fetchFileOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, fileIdentifier);
    if (ngFile.isFolder()) {
      throw new InvalidArgumentsException(
          format("Downloading folder not supported, fileIdentifier: %s", fileIdentifier));
    }

    File file = new File(Files.createTempDir(), ngFile.getName());
    log.info("Start downloading file, fileIdentifier: {}, filePath: {}", fileIdentifier, file.getPath());
    return fileService.download(ngFile.getFileUuid(), file, FILE_STORE);
  }

  @Override
  public boolean delete(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File identifier cannot be empty");
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException("Account identifier cannot be null or empty");
    }
    if (ROOT_FOLDER_IDENTIFIER.equals(identifier)) {
      throw new InvalidArgumentsException(format("Root folder [%s] can not be deleted.", ROOT_FOLDER_IDENTIFIER));
    }

    NGFile file = fetchFileOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    fileReferenceService.validateReferenceByAndThrow(file);

    return deleteFileOrFolder(file);
  }

  @Override
  public FolderNodeDTO listFolderNodes(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull FolderNodeDTO folderNodeDTO) {
    FolderNodeDTO updatedFolderNode =
        updateFolderNode(accountIdentifier, orgIdentifier, projectIdentifier, folderNodeDTO);
    return populateFolderNode(updatedFolderNode, accountIdentifier, orgIdentifier, projectIdentifier);
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
    NGFile file = fetchFileOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
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
    Criteria criteria = createScopeCriteria(scope);
    criteria.and(NGFiles.type).is(NGFileType.FILE);

    Aggregation aggregation = Aggregation.newAggregation(
        match(criteria), group(NGFiles.createdBy), sort(Sort.Direction.ASC, NGFiles.createdBy));

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

  private boolean isFileExistsByIdentifier(FileDTO fileDto) {
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(fileDto.getAccountIdentifier(),
            fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(), fileDto.getIdentifier())
        .isPresent();
  }

  private boolean isFileExistByName(FileDTO fileDto) {
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
            fileDto.getAccountIdentifier(), fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(),
            fileDto.getParentIdentifier(), fileDto.getName())
        .isPresent();
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

  private NGFile fetchFileOrThrow(
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

  // in the case when we need to return the whole folder structure, create recursion on this method
  private FolderNodeDTO populateFolderNode(
      FolderNodeDTO folderNode, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<FileStoreNodeDTO> fileStoreNodes =
        listFolderChildren(accountIdentifier, orgIdentifier, projectIdentifier, folderNode.getIdentifier());
    for (FileStoreNodeDTO node : fileStoreNodes) {
      folderNode.addChild(node);
    }
    return folderNode;
  }

  private List<FileStoreNodeDTO> listFolderChildren(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderIdentifier) {
    return listFilesByParentIdentifierSortedByLastModifiedAt(
        accountIdentifier, orgIdentifier, projectIdentifier, folderIdentifier)
        .stream()
        .filter(Objects::nonNull)
        .map(ngFile
            -> ngFile.isFolder() ? FileStoreNodeDTOMapper.getFolderNodeDTO(ngFile)
                                 : FileStoreNodeDTOMapper.getFileNodeDTO(ngFile, null))
        .collect(Collectors.toList());
  }

  private List<NGFile> listFilesByParentIdentifierSortedByLastModifiedAt(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String parentIdentifier) {
    return fileStoreRepository.findAllAndSort(
        createCriteriaByScopeAndParentIdentifier(
            Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), parentIdentifier),
        createSortByLastModifiedAtDesc());
  }

  private FolderNodeDTO updateFolderNode(final String accountIdentifier, final String orgIdentifier,
      final String projectIdentifier, FolderNodeDTO folderNodeDTO) {
    final String folderIdentifier = folderNodeDTO.getIdentifier();
    final String folderName = folderNodeDTO.getName();
    if (ROOT_FOLDER_IDENTIFIER.equals(folderIdentifier) || ROOT_FOLDER_NAME.equals(folderName)) {
      return FileStoreNodeDTOMapper.getFolderNodeDTO(folderNodeDTO, ROOT_FOLDER_PARENT_IDENTIFIER, ROOT_FOLDER_PATH);
    }

    NGFile ngFile = fetchFileOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, folderIdentifier);
    if (ngFile.isFile()) {
      throw new InvalidArgumentsException(format(
          "Required folder, found file with identifier [%s], accountIdentifier [%s], orgIdentifier [%s] and projectIdentifier [%s]",
          folderIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    }

    return FileStoreNodeDTOMapper.getFolderNodeDTO(ngFile);
  }

  private boolean deleteFileOrFolder(NGFile fileOrFolder) {
    if (NGFileType.FOLDER.equals(fileOrFolder.getType())) {
      return deleteFolder(fileOrFolder);
    } else {
      return deleteFile(fileOrFolder);
    }
  }

  private boolean deleteFolder(NGFile folder) {
    List<NGFile> childrenFiles = listFilesByParent(folder);
    if (!isEmpty(childrenFiles)) {
      childrenFiles.stream().filter(Objects::nonNull).forEach(this::deleteFileOrFolder);
    }

    return fileFailsafeService.deleteAndPublish(folder);
  }

  private List<NGFile> listFilesByParent(NGFile parent) {
    return fileStoreRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
        parent.getAccountIdentifier(), parent.getOrgIdentifier(), parent.getProjectIdentifier(),
        parent.getIdentifier());
  }

  private boolean deleteFile(NGFile file) {
    try {
      fileService.deleteFile(file.getFileUuid(), FILE_STORE);
    } catch (Exception e) {
      log.error("Failed to delete file from file store [{}].", file.getIdentifier(), e);
    }

    return fileFailsafeService.deleteAndPublish(file);
  }

  private void validateCreationFileDto(FileDTO fileDto) {
    if (isFileExistByName(fileDto)) {
      throw new DuplicateFieldException(getDuplicateEntityNameMessage(fileDto));
    }

    if (ROOT_FOLDER_IDENTIFIER.equals(fileDto.getIdentifier()) || isFileExistsByIdentifier(fileDto)) {
      throw new DuplicateFieldException(getDuplicateEntityIdentifierMessage(fileDto));
    }

    if (isEmpty(fileDto.getParentIdentifier())) {
      throw new InvalidArgumentsException("Parent folder identifier is mandatory.");
    }

    if (!parentFolderExists(fileDto)) {
      throw new InvalidArgumentsException(
          format("Parent folder with identifier [%s] does not exist", fileDto.getParentIdentifier()));
    }
  }

  private void validateUpdateFileDto(FileDTO fileDto, String identifier) {
    if (isEmpty(identifier)) {
      throw new InvalidArgumentsException("File or folder identifier cannot be empty");
    }

    if (identifier.equals(fileDto.getParentIdentifier())) {
      throw new InvalidArgumentsException(
          format("File or folder identifier [%s] cannot be the same as parent folder identifier [%s]", identifier,
              fileDto.getParentIdentifier()));
    }

    if (!parentFolderExists(fileDto)) {
      throw new InvalidArgumentsException(
          format("Parent folder with identifier [%s] does not exist", fileDto.getParentIdentifier()));
    }
  }

  private boolean parentFolderExists(FileDTO fileDto) {
    if (ROOT_FOLDER_IDENTIFIER.equals(fileDto.getParentIdentifier())) {
      return true;
    }
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(fileDto.getAccountIdentifier(),
            fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(), fileDto.getParentIdentifier())
        .filter(NGFile::isFolder)
        .isPresent();
  }

  private String createPath(FileDTO fileDto) {
    String parentIdentifier = fileDto.getParentIdentifier();
    String name = fileDto.getName();

    if (ROOT_FOLDER_IDENTIFIER.equals(parentIdentifier)) {
      return format("%s%s%s%s", PATH_SEPARATOR, ROOT_FOLDER_NAME, PATH_SEPARATOR, name);
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
}
