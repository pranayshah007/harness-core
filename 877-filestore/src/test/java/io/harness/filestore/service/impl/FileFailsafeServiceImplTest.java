/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.entities.NGFile.builder;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.filestore.dto.mapper.FileDTOMapper;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.FileActivityService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.spring.FileStoreRepository;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileFailsafeServiceImplTest extends CategoryTest {
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private FileActivityService fileActivityService;
  @Mock private OutboxService outboxService;
  @Mock private FileStoreRepository fileStoreRepository;

  @InjectMocks private FileFailsafeServiceImpl fileFailsafeService;

  @Before
  public void setUp() {
    when(fileStoreRepository.save(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveAndPublish() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    FileDTO fileDTO = FileDTOMapper.getFileDTOFromNGFile(ngFile);

    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocationOnMock -> invocationOnMock.getArgument(0, TransactionCallback.class).doInTransaction(null));

    FileDTO savedFileDTO = fileFailsafeService.saveAndPublish(ngFile);

    assertThat(savedFileDTO).isNotNull();
    assertThat(savedFileDTO).isEqualTo(fileDTO);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveAndPublishWithException() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    when(transactionTemplate.execute(any()))
        .thenThrow(
            new DuplicateKeyException(format("The entity with %s identifier already exists", ngFile.getIdentifier())));

    assertThatThrownBy(() -> fileFailsafeService.saveAndPublish(ngFile))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(format("Try using another identifier, [%s] already exists", ngFile.getIdentifier()));
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testUpdateAndPublishWithException() {
    NGFile oldNgFile = createNgFile("oldFileName", "oldIdentifier");
    NGFile newNgFile = createNgFile("newFileName", "newIdentifier");
    when(transactionTemplate.execute(any()))
        .thenThrow(new DuplicateKeyException(
            format("The entity with %s identifier already exists", newNgFile.getIdentifier())));

    assertThatThrownBy(() -> fileFailsafeService.updateAndPublish(oldNgFile, newNgFile))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(format("Try using another name, [%s] already exists in parent folder [%s]", newNgFile.getName(),
            newNgFile.getParentIdentifier()));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateAndPublish() {
    NGFile oldNgFile = createNgFile("oldFileName", "oldIdentifier");
    NGFile newNgFile = createNgFolder("newFileName", "newIdentifier");
    FileDTO fileDTO = FileDTOMapper.getFileDTOFromNGFile(newNgFile);
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocationOnMock -> invocationOnMock.getArgument(0, TransactionCallback.class).doInTransaction(null));
    FileDTO updatedFileDTO = fileFailsafeService.updateAndPublish(oldNgFile, newNgFile);

    assertThat(updatedFileDTO).isNotNull();
    assertThat(updatedFileDTO).isEqualTo(fileDTO);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteAndPublish() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocationOnMock -> invocationOnMock.getArgument(0, TransactionCallback.class).doInTransaction(null));
    boolean deletedFileDTO = fileFailsafeService.deleteAndPublish(ngFile, false);

    assertThat(deletedFileDTO).isTrue();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testForceDeleteAndPublish() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocationOnMock -> invocationOnMock.getArgument(0, TransactionCallback.class).doInTransaction(null));
    boolean deletedFileDTO = fileFailsafeService.deleteAndPublish(ngFile, true);

    assertThat(deletedFileDTO).isTrue();
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testDeleteAndPublishWithException() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    when(transactionTemplate.execute(any()))
        .thenThrow(new IllegalArgumentException("The given entity must not be null!"));

    assertThatThrownBy(() -> fileFailsafeService.deleteAndPublish(ngFile, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The given entity must not be null!");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testNoExceptionIsThrown() {
    doThrow(DuplicateKeyException.class).when(fileActivityService).createFileCreationActivity(any(), any());

    doThrow(DuplicateKeyException.class).when(fileActivityService).createFileUpdateActivity(any(), any());

    doThrow(DuplicateKeyException.class).when(fileActivityService).deleteAllActivities(any(), any());

    assertThatCode(() -> { fileFailsafeService.createFileCreationActivity(new FileDTO()); }).doesNotThrowAnyException();

    assertThatCode(() -> { fileFailsafeService.createFileUpdateActivity(new FileDTO()); }).doesNotThrowAnyException();

    assertThatCode(() -> {
      fileFailsafeService.deleteActivities("account", "org", "proj", "ident");
    }).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testInvokeDeleteActivities() {
    fileFailsafeService.deleteActivities("account", "org", "proj", "ident");

    verify(fileActivityService).deleteAllActivities("account", "account/org/proj/ident");
  }

  private NGFile createNgFile(String name, String identifier) {
    return builder().type(NGFileType.FILE).name(name).description("Description").identifier(identifier).build();
  }

  private NGFile createNgFolder(String name, String identifier) {
    return builder().type(NGFileType.FOLDER).name(name).description("Description").identifier(identifier).build();
  }
}
