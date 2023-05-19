/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.exception.ExportExecutionsException;
import io.harness.rule.Owner;

import software.wings.service.intfc.FileService;

import com.google.inject.Inject;
import java.io.File;
import java.io.OutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExportExecutionsFileServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "aid";
  private static final String FILE_ID = "fid";

  @Mock private FileService fileService;
  @Inject @InjectMocks private ExportExecutionsFileService exportExecutionsFileService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUploadFile() {
    File file = mock(File.class);
    assertThatThrownBy(() -> exportExecutionsFileService.uploadFile(ACCOUNT_ID, file))
        .isInstanceOf(ExportExecutionsException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testPrepareFileMetadata() {
    File file = mock(File.class);
    when(file.getName()).thenReturn("fn");
    FileMetadata fileMetadata = exportExecutionsFileService.prepareFileMetadata(ACCOUNT_ID, file);
    assertThat(fileMetadata).isNotNull();
    assertThat(fileMetadata.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(fileMetadata.getFileName()).isEqualTo("fn");
    assertThat(fileMetadata.getFileUuid()).isNotNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testDownloadFileToStream() {
    OutputStream outputStream = mock(OutputStream.class);
    exportExecutionsFileService.downloadFileToStream(FILE_ID, outputStream);
    verify(fileService, times(1)).downloadToStream(eq(FILE_ID), eq(outputStream), eq(FileBucket.EXPORT_EXECUTIONS));

    doThrow(new RuntimeException(""))
        .when(fileService)
        .downloadToStream(eq(FILE_ID), eq(outputStream), eq(FileBucket.EXPORT_EXECUTIONS));
    assertThatThrownBy(() -> exportExecutionsFileService.downloadFileToStream(FILE_ID, outputStream))
        .isInstanceOf(ExportExecutionsException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testDeleteFile() {
    exportExecutionsFileService.deleteFile(null);
    verify(fileService, never()).deleteFile(any(), any());

    OutputStream outputStream = mock(OutputStream.class);
    exportExecutionsFileService.deleteFile(FILE_ID);
    verify(fileService, times(1)).deleteFile(eq(FILE_ID), eq(FileBucket.EXPORT_EXECUTIONS));

    doThrow(new RuntimeException("")).when(fileService).deleteFile(eq(FILE_ID), eq(FileBucket.EXPORT_EXECUTIONS));
    assertThatThrownBy(() -> exportExecutionsFileService.deleteFile(FILE_ID))
        .isInstanceOf(ExportExecutionsException.class);
  }
}
