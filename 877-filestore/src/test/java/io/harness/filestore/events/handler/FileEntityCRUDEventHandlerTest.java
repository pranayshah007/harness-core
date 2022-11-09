/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.events.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.FileStoreTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.FILE_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.FILE_NAME;
import static io.harness.filestore.FileStoreTestConstants.ORG_IDENTIFIER;
import static io.harness.filestore.FileStoreTestConstants.PROJECT_IDENTIFIER;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@OwnedBy(CDP)
public class FileEntityCRUDEventHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private FileStoreService fileStoreService;
  @InjectMocks private FileEntityCRUDEventHandler fileEntityCRUDEventHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteAssociatedFiles() {
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, Collections.emptyList());
    when(fileStoreService.listFilesAndFolders(
             eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.singletonList(getFileDTO())));
    doNothing().when(fileStoreService).deleteBatch(anyString(), anyString(), anyString(), any());

    final ArgumentCaptor<List<String>> fileIdentifiersCaptor = ArgumentCaptor.forClass(List.class);
    fileEntityCRUDEventHandler.deleteAssociatedFiles(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    verify(fileStoreService, times(1)).deleteBatch(any(), any(), any(), fileIdentifiersCaptor.capture());

    List<String> fileIdentifiersCaptorValue = fileIdentifiersCaptor.getValue();
    assertThat(fileIdentifiersCaptorValue.size()).isEqualTo(1);
    assertThat(fileIdentifiersCaptorValue.get(0)).isEqualTo(FILE_IDENTIFIER);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteAssociatedFilesWithException() {
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, Collections.emptyList());
    when(
        fileStoreService.listFilesAndFolders(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, pageRequest))
        .thenThrow(new InvalidRequestException("Error message"));

    boolean deletedFiles =
        fileEntityCRUDEventHandler.deleteAssociatedFiles(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(deletedFiles).isFalse();
  }

  private FileDTO getFileDTO() {
    return FileDTO.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier(FILE_IDENTIFIER)
        .name(FILE_NAME)
        .type(NGFileType.FILE)
        .build();
  }
}
