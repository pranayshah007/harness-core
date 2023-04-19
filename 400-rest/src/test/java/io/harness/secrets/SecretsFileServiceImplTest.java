/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.FileBucket.CONFIGS;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.security.SimpleEncryption.CHARSET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.persistence.BaseFile;
import software.wings.service.intfc.FileService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class SecretsFileServiceImplTest extends CategoryTest {
  private FileService fileService;
  private MainConfiguration mainConfiguration;
  private SecretsFileService secretsFileService;

  @Before
  public void setup() {
    fileService = mock(FileService.class);
    mainConfiguration = mock(MainConfiguration.class);
    secretsFileService = new SecretsFileServiceImpl(fileService, mainConfiguration);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetFileContents() {
    String fileContent = UUIDGenerator.generateUuid();
    String fileId = UUIDGenerator.generateUuid();
    doAnswer(invocationOnMock -> {
      ByteArrayOutputStream os = (ByteArrayOutputStream) invocationOnMock.getArguments()[1];
      os.write(fileContent.getBytes(CHARSET));
      return null;
    })
        .when(fileService)
        .downloadToStream(eq(fileId), any(ByteArrayOutputStream.class), eq(CONFIGS));
    char[] returnedContent = secretsFileService.getFileContents(fileId);
    assertThat(returnedContent).isEqualTo(fileContent.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testDeleteFile() {
    String fileId = UUIDGenerator.generateUuid();
    secretsFileService.deleteFile(fileId.toCharArray());
    verify(fileService, times(1)).deleteFile(fileId, CONFIGS);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetFileSizeLimit() {
    FileUploadLimit limits = new FileUploadLimit();
    when(mainConfiguration.getFileUploadLimits()).thenReturn(limits);
    long limit = secretsFileService.getFileSizeLimit();
    assertThat(limits.getEncryptedFileLimit()).isEqualTo(limit);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateFile() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String accountId = UUIDGenerator.generateUuid();
    char[] fileContent = UUIDGenerator.generateUuid().toCharArray();
    String fileId = UUIDGenerator.generateUuid();
    when(fileService.saveFile(any(BaseFile.class), any(ByteArrayInputStream.class), eq(CONFIGS))).thenReturn(fileId);
    String returnedFileId = secretsFileService.createFile(name, accountId, fileContent);
    assertThat(returnedFileId).isEqualTo(fileId);

    ArgumentCaptor<BaseFile> fileCaptor = ArgumentCaptor.forClass(BaseFile.class);
    ArgumentCaptor<ByteArrayInputStream> inputStreamCaptor = ArgumentCaptor.forClass(ByteArrayInputStream.class);
    verify(fileService, times(1)).saveFile(fileCaptor.capture(), inputStreamCaptor.capture(), eq(CONFIGS));

    BaseFile baseFile = fileCaptor.getValue();
    assertThat(baseFile.getFileName()).isEqualTo(name);
    assertThat(baseFile.getAccountId()).isEqualTo(accountId);

    ByteArrayInputStream stream = inputStreamCaptor.getValue();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    IOUtils.copy(stream, os);
    assertThat(CHARSET.decode(ByteBuffer.wrap(os.toByteArray())).array()).isEqualTo(fileContent);
  }
}
