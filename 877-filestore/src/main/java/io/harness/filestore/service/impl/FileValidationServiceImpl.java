/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service.impl;

import static io.harness.FileStoreConstants.ROOT_FOLDER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.FileValidationService;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.repositories.spring.FileStoreRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class FileValidationServiceImpl implements FileValidationService {
  @Inject private FileStoreRepository fileStoreRepository;
  @Inject private AccountService accountService;
  @Inject private OrganizationService organizationService;
  @Inject private ProjectService projectService;

  public boolean isFileExistByName(FileDTO fileDto) {
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
            fileDto.getAccountIdentifier(), fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(),
            fileDto.getParentIdentifier(), fileDto.getName())
        .isPresent();
  }

  public boolean isFileExistsByIdentifier(FileDTO fileDto) {
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(fileDto.getAccountIdentifier(),
            fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(), fileDto.getIdentifier())
        .isPresent();
  }

  public boolean parentFolderExists(FileDTO fileDto) {
    if (ROOT_FOLDER_IDENTIFIER.equals(fileDto.getParentIdentifier())) {
      validateExistenceOfAccountOrOrgOrProject(fileDto);
      return true;
    }
    return fileStoreRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(fileDto.getAccountIdentifier(),
            fileDto.getOrgIdentifier(), fileDto.getProjectIdentifier(), fileDto.getParentIdentifier())
        .filter(NGFile::isFolder)
        .isPresent();
  }

  private void validateExistenceOfAccountOrOrgOrProject(@NotNull FileDTO fileDto) {
    String accountIdentifier = fileDto.getAccountIdentifier();
    String orgIdentifier = fileDto.getOrgIdentifier();
    String projectIdentifier = fileDto.getProjectIdentifier();

    if (isNotEmpty(projectIdentifier)) {
      projectService.get(accountIdentifier, orgIdentifier, projectIdentifier)
          .orElseThrow(()
                           -> new InvalidArgumentsException(format(
                               "Project with identifier [%s] does not exist, orgIdentifier: %s, accountIdentifier: %s",
                               projectIdentifier, orgIdentifier, accountIdentifier)));

    } else if (isNotEmpty(orgIdentifier)) {
      organizationService.get(accountIdentifier, orgIdentifier)
          .orElseThrow(()
                           -> new InvalidArgumentsException(
                               format("Org with identifier [%s] does not exist, accountIdentifier: %s", orgIdentifier,
                                   accountIdentifier)));
    } else {
      accountService.getAccount(accountIdentifier);
    }
  }
}
