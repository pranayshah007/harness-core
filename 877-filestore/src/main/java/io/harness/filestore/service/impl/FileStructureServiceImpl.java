/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.FileBucket.FILE_STORE;
import static io.harness.filestore.entities.NGFile.NGFiles;
import static io.harness.filestore.utils.FileStoreNodeUtils.joinFolderNames;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createCriteriaByScopeAndParentIdentifier;
import static io.harness.repositories.FileStoreRepositoryCriteriaCreator.createSortByLastModifiedAtDesc;
import static io.harness.security.SimpleEncryption.CHARSET;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnexpectedException;
import io.harness.filestore.dto.mapper.FileStoreNodeDTOMapper;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.FileStructureService;
import io.harness.manage.ManagedExecutorService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.pms.utils.CompletableFutures;
import io.harness.repositories.spring.FileStoreRepository;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import software.wings.service.intfc.FileService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class FileStructureServiceImpl implements FileStructureService {
  private static final int LIST_SUB_FOLDERS_ON_SAME_DEEP_LEVEL_TIMEOUT_IN_MIN = 5;

  @Inject private FileService fileService;
  @Inject private FileStoreRepository fileStoreRepository;
  private final Executor foldersExecutor = new ManagedExecutorService(Executors.newFixedThreadPool(4));

  @Override
  public void createFolderTreeStructure(FolderNodeDTO folder, Scope scope, boolean includeContent) {
    createTreeStructure(Collections.singletonList(folder), scope, includeContent);
  }

  private void createTreeStructure(List<FolderNodeDTO> foldersOnSameDeepLevel, Scope scope, boolean includeContent) {
    if (isEmpty(foldersOnSameDeepLevel)) {
      return;
    }

    CompletableFutures<List<FolderNodeDTO>> listSubFoldersOnSameDeepLevelCF = new CompletableFutures<>(foldersExecutor);
    foldersOnSameDeepLevel.forEach(folder -> listSubFoldersOnSameDeepLevelCF.supplyAsync(() -> {
      populateFolderChildren(folder, scope, includeContent);
      return folder.getChildren()
          .stream()
          .filter(fileStoreNodeDTO -> fileStoreNodeDTO.getType() == NGFileType.FOLDER)
          .map(FolderNodeDTO.class ::cast)
          .collect(Collectors.toList());
    }));

    final List<FolderNodeDTO> subFoldersOnSameDeepLevel = new ArrayList<>();
    long start = System.currentTimeMillis();
    try {
      List<List<FolderNodeDTO>> subFoldersOnSameDeepLevelList = listSubFoldersOnSameDeepLevelCF.allOf().get(
          LIST_SUB_FOLDERS_ON_SAME_DEEP_LEVEL_TIMEOUT_IN_MIN, TimeUnit.MINUTES);
      subFoldersOnSameDeepLevelList.forEach(subFoldersOnSameDeepLevel::addAll);
    } catch (Exception ex) {
      log.error(format("Unexpected folder tree structure creation error: %s", ex.getMessage()), ex);
      throw new UnexpectedException(
          format("Unexpected folder tree structure creation  error: %s", ex.getMessage()), ex);
    } finally {
      log.info("Folder tree structure creation time {}ms for folders on the same level {}",
          System.currentTimeMillis() - start, joinFolderNames(foldersOnSameDeepLevel, ","));
    }

    createTreeStructure(subFoldersOnSameDeepLevel, scope, includeContent);
  }

  private void populateFolderChildren(FolderNodeDTO parentFolder, Scope scope, boolean includeContent) {
    listFilesByParentIdentifierSortedByLastModifiedAt(scope, parentFolder.getIdentifier())
        .stream()
        .filter(Objects::nonNull)
        .map(ngFile
            -> ngFile.isFolder() ? FileStoreNodeDTOMapper.getFolderNodeDTO(ngFile)
                                 : FileStoreNodeDTOMapper.getFileNodeDTO(
                                     ngFile, includeContent ? getFileContent(ngFile.getFileUuid()) : null))
        .forEach(parentFolder::addChild);
  }

  private List<NGFile> listFilesByParentIdentifierSortedByLastModifiedAt(Scope scope, String parentIdentifier) {
    return fileStoreRepository.findAllAndSort(
        createCriteriaByScopeAndParentIdentifier(scope, parentIdentifier), createSortByLastModifiedAtDesc());
  }

  @Override
  public String getFileContent(@NotNull final String fileUuid) {
    if (isEmpty(fileUuid)) {
      throw new InvalidArgumentsException("File UUID cannot be null or empty");
    }

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    fileService.downloadToStream(fileUuid, os, FILE_STORE);
    return CHARSET.decode(ByteBuffer.wrap(os.toByteArray())).toString();
  }

  @Override
  public List<NGFile> listFolderChildrenByPath(NGFile folder) {
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifierNotAndPathStartsWith(
            folder.getAccountIdentifier(), folder.getOrgIdentifier(), folder.getProjectIdentifier(),
            folder.getIdentifier(), folder.getPath(), Sort.by(Sort.Direction.DESC, NGFiles.path));
  }
  @Override
  public List<String> listFolderChildrenFQNs(NGFile folder) {
    List<NGFile> childrenFiles = listFolderChildrenByPath(folder);
    return childrenFiles.stream().map(mapNgFileToFQN()).collect(Collectors.toList());
  }

  private Function<NGFile, String> mapNgFileToFQN() {
    return file
        -> FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            file.getAccountIdentifier(), file.getOrgIdentifier(), file.getProjectIdentifier(), file.getIdentifier());
  }
}
