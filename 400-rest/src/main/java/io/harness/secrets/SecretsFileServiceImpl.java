/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.FileBucket.CONFIGS;
import static io.harness.security.SimpleEncryption.CHARSET;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;

import software.wings.app.MainConfiguration;
import software.wings.persistence.BaseFile;
import software.wings.service.intfc.FileService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@Singleton
@OwnedBy(PL)
public class SecretsFileServiceImpl implements SecretsFileService {
  private final FileService fileService;
  private final MainConfiguration configuration;

  @Inject
  public SecretsFileServiceImpl(FileService fileService, MainConfiguration mainConfiguration) {
    this.fileService = fileService;
    this.configuration = mainConfiguration;
  }

  @Override
  public String createFile(String name, String accountId, char[] fileContent) {
    BaseFile baseFile = new BaseFile();
    baseFile.setFileName(name);
    baseFile.setAccountId(accountId);
    baseFile.setFileUuid(UUIDGenerator.generateUuid());
    return fileService.saveFile(
        baseFile, new ByteArrayInputStream(CHARSET.encode(CharBuffer.wrap(fileContent)).array()), CONFIGS);
  }

  @Override
  public long getFileSizeLimit() {
    return configuration.getFileUploadLimits().getEncryptedFileLimit();
  }

  @Override
  public char[] getFileContents(String fileId) {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    fileService.downloadToStream(fileId, os, CONFIGS);
    return CHARSET.decode(ByteBuffer.wrap(os.toByteArray())).array();
  }

  @Override
  public void deleteFile(char[] fileId) {
    fileService.deleteFile(String.valueOf(fileId), CONFIGS);
  }
}
