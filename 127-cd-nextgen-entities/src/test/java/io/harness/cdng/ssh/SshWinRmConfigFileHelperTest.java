/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.FILIP;

import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileReference;
import io.harness.category.element.UnitTests;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class SshWinRmConfigFileHelperTest extends CategoryTest {
  @Mock private FileStoreService fileStoreService;

  @InjectMocks SshWinRmConfigFileHelper sshWinRmConfigFileHelper;

  @Before
  public void prepare() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldForceUnixLineEndingFormat() {
    // Given
    String contentFromFileStore = "line1\r\nline2\r\n\r\nline3";

    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(of(FileNodeDTO.builder().content(contentFromFileStore).build()));

    // When
    String fileContent = sshWinRmConfigFileHelper.fetchFileContent(FileReference.builder().build());

    // Then
    assertThat(fileContent).doesNotContain("\r").isEqualTo("line1\nline2\n\nline3");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldNotForceUnixLineEndingFormat() {
    // Given
    String contentFromFileStore = "line1\r\nline2\r\n\r\nline3";

    when(fileStoreService.getWithChildrenByPath(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(of(FileNodeDTO.builder().content(contentFromFileStore).build()));

    // When
    String fileContent = sshWinRmConfigFileHelper.fetchFileContent(FileReference.builder().build(), false);

    // Then
    assertThat(fileContent).contains("\r").isEqualTo(contentFromFileStore);
  }
}
