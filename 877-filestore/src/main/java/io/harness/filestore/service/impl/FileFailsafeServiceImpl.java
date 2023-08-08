/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service.impl;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.DuplicateFieldException;
import io.harness.filestore.dto.mapper.FileDTOMapper;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.events.FileCreateEvent;
import io.harness.filestore.events.FileDeleteEvent;
import io.harness.filestore.events.FileForceDeleteEvent;
import io.harness.filestore.events.FileUpdateEvent;
import io.harness.filestore.service.FileActivityService;
import io.harness.filestore.service.FileFailsafeService;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.spring.FileStoreRepository;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(CDP)
@Singleton
@Slf4j
public class FileFailsafeServiceImpl implements FileFailsafeService {
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private final FileStoreRepository fileStoreRepository;
  private final FileActivityService fileActivityService;

  @Inject
  public FileFailsafeServiceImpl(OutboxService outboxService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      FileStoreRepository fileStoreRepository, FileActivityService fileActivityService) {
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.fileStoreRepository = fileStoreRepository;
    this.fileActivityService = fileActivityService;
  }

  @Override
  public FileDTO saveAndPublish(NGFile ngFile) {
    try {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        NGFile savedNgFile = fileStoreRepository.save(ngFile);
        FileDTO fileDTOFromSavedNGFile = FileDTOMapper.getFileDTOFromNGFile(savedNgFile);

        createFileCreationActivity(fileDTOFromSavedNGFile);
        outboxService.save(new FileCreateEvent(fileDTOFromSavedNGFile.getAccountIdentifier(), fileDTOFromSavedNGFile));
        return fileDTOFromSavedNGFile;
      }));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("Try using another identifier, [%s] already exists", ngFile.getIdentifier()), USER, ex);
    }
  }

  @Override
  public FileDTO updateAndPublish(NGFile oldNGFile, NGFile newNGFile) {
    try {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        NGFile newNgFile = fileStoreRepository.save(newNGFile);
        FileDTO newFileDTO = FileDTOMapper.getFileDTOFromNGFile(newNgFile);
        FileDTO oldFileDTO = FileDTOMapper.getFileDTOFromNGFile(oldNGFile);

        createFileUpdateActivity(newFileDTO);
        outboxService.save(new FileUpdateEvent(newFileDTO.getAccountIdentifier(), newFileDTO, oldFileDTO));
        return newFileDTO;
      }));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("Try using another name, [%s] already exists in parent folder [%s]", newNGFile.getName(),
              newNGFile.getParentIdentifier()),
          USER, ex);
    }
  }

  @Override
  public boolean deleteAndPublish(NGFile ngFile, boolean forceDelete) {
    try {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        fileStoreRepository.delete(ngFile);

        deleteActivities(ngFile.getAccountIdentifier(), ngFile.getOrgIdentifier(), ngFile.getProjectIdentifier(),
            ngFile.getIdentifier());

        if (forceDelete) {
          outboxService.save(
              new FileForceDeleteEvent(ngFile.getAccountIdentifier(), FileDTOMapper.getFileDTOFromNGFile(ngFile)));
        } else {
          outboxService.save(
              new FileDeleteEvent(ngFile.getAccountIdentifier(), FileDTOMapper.getFileDTOFromNGFile(ngFile)));
        }
        log.info("{} [{}] deleted.", ngFile.isFile() ? "File" : "Folder", ngFile.getName());
        return true;
      }));
    } catch (Exception ex) {
      log.error("Failed to delete {} [{}].", ngFile.isFile() ? "file" : "folder", ngFile.getName(), ex);
      throw ex;
    }
  }

  @VisibleForTesting
  void createFileCreationActivity(FileDTO fileDTO) {
    try {
      fileActivityService.createFileCreationActivity(fileDTO.getAccountIdentifier(), fileDTO);
    } catch (Exception ex) {
      log.error("Error while creating file creation activity, name: {}, identifier: {}", fileDTO.getName(),
          fileDTO.getIdentifier(), ex);
    }
  }

  @VisibleForTesting
  void createFileUpdateActivity(FileDTO fileDTO) {
    try {
      fileActivityService.createFileUpdateActivity(fileDTO.getAccountIdentifier(), fileDTO);
    } catch (Exception ex) {
      log.error("Error while creating file update activity, name: {}, identifier: {}", fileDTO.getName(),
          fileDTO.getIdentifier(), ex);
    }
  }

  @VisibleForTesting
  void deleteActivities(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    try {
      String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      fileActivityService.deleteAllActivities(accountIdentifier, fullyQualifiedIdentifier);
    } catch (Exception ex) {
      log.error("Error while deleting file activity identifier: {}", identifier, ex);
    }
  }
}
