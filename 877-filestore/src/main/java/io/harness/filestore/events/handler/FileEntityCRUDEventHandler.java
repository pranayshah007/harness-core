/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.events.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class FileEntityCRUDEventHandler {
  private static final int DEFAULT_PAGE_SIZE = 10;

  private FileStoreService fileStoreService;

  public boolean deleteAssociatedFiles(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      List<String> fileIdentifiers = fetchAllFileIdentifiers(accountIdentifier, orgIdentifier, projectIdentifier);
      log.info(
          "Total number of files and folder to be deleted: {}, accountIdentifier: {}, orgIdentifier: {}, projectIdentifier: {}",
          fileIdentifiers.size(), accountIdentifier, orgIdentifier, projectIdentifier);
      fileStoreService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier, fileIdentifiers);
    } catch (Exception ex) {
      log.error(
          "Exception occurred in delete files and folders batch call, accountIdentifier: {}, orgIdentifier: {}, projectIdentifier: {}",
          accountIdentifier, orgIdentifier, projectIdentifier, ex);
      return false;
    }

    return true;
  }

  private List<String> fetchAllFileIdentifiers(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Page<FileDTO> pagedFilesList = null;
    List<String> fileIdentifiers = new ArrayList<>();
    do {
      int page = pagedFilesList == null ? 0 : pagedFilesList.getNumber() + 1;
      Pageable pageRequest = PageUtils.getPageRequest(page, DEFAULT_PAGE_SIZE, Collections.emptyList());
      pagedFilesList =
          fileStoreService.listFilesAndFolders(accountIdentifier, orgIdentifier, projectIdentifier, null, pageRequest);
      fileIdentifiers.addAll(
          pagedFilesList.getContent().stream().map(FileDTO::getIdentifier).collect(Collectors.toList()));
    } while (pagedFilesList.hasNext());

    return fileIdentifiers;
  }
}
