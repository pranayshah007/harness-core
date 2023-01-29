/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.service;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.utils.WingsTestConstants.FILE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.persistence.BaseFile;
import software.wings.rules.Integration;
import software.wings.service.intfc.FileService;

import com.google.common.io.Files;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

@Integration
public class FileServiceIntegrationTest extends WingsBaseTest {
  /**
   * The Temporary folder.
   */
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Inject private FileService fileService;
  private File tempFile;

  /**
   * Setup files.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Before
  public void setupFiles() throws IOException {
    tempFile = temporaryFolder.newFile();
    Files.write("Dummy".getBytes(), tempFile);
  }

  /**
   * Should save file with metadata.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = UTKARSH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldSaveFileWithMetadata() throws Exception {
    FileMetadata fileMetadata = FileMetadata.builder()
                                    .fileName("dummy.txt")
                                    .mimeType("text/plain")
                                    .fileUuid(UUIDGenerator.generateUuid())
                                    .build();
    String fileId = fileService.saveFile(fileMetadata, new FileInputStream(tempFile), FileBucket.ARTIFACTS);
    assertThat(fileService.getFileMetadata(fileId, FileBucket.ARTIFACTS)).isNotNull();
  }

  /**
   * Should throw exception when file name is null with file metadata.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldThrowExceptionWhenFileNameIsNullWithFileMetadata() throws Exception {
    FileMetadata fileMetadata = FileMetadata.builder().mimeType("text/plain").build();
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> fileService.saveFile(fileMetadata, new FileInputStream(tempFile), FileBucket.ARTIFACTS));
  }

  /**
   * Should save base file.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldSaveBaseFile() throws Exception {
    final BaseFile baseFile = new BaseFile();
    baseFile.setName("dummy.txt");
    baseFile.setFileName("dummy.txt");

    assertThat(fileService.saveFile(baseFile, new FileInputStream(tempFile), FileBucket.ARTIFACTS)).isNotNull();
  }

  /**
   * Should save base file.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldUpdateEntityId() throws Exception {
    final BaseFile baseFile = new BaseFile();
    baseFile.setName("dummy.txt");
    baseFile.setFileName("dummy.txt");

    String fileId = fileService.saveFile(baseFile, new FileInputStream(tempFile), FileBucket.ARTIFACTS);
    assertThat(fileId).isNotNull();
    fileService.updateParentEntityIdAndVersion(null, FILE_ID, 1, fileId, null, FileBucket.ARTIFACTS);
    assertThat(fileService.getAllFileIds(FILE_ID, FileBucket.ARTIFACTS)).hasSize(1).contains(fileId);
  }

  /**
   * Should throw exception when file name is null with base file.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldThrowExceptionWhenFileNameIsNullWithBaseFile() throws Exception {
    final BaseFile baseFile = new BaseFile();
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> fileService.saveFile(baseFile, new FileInputStream(tempFile), FileBucket.ARTIFACTS))
        .isNotNull();
  }
}
