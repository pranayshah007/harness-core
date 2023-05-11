/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.utils.FileStoreNodeUtils.mapFileNodes;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filestore.dto.mapper.FileStoreNodeDTOMapper;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.entities.NGFile;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.rule.Owner;

import java.util.List;
import lombok.Builder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileStoreNodeUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testMapFileNodesWithFile() {
    FileNodeDTO file = getFile("fileName", "fileContent", 20L);
    List<FileParameters> fileParameters = mapFileNodes(file,
        fileNode
        -> FileParameters.builder()
               .content(fileNode.getContent())
               .fileName(fileNode.getName())
               .fileSize(fileNode.getSize())
               .build());

    assertThat(fileParameters).isNotNull();
    assertThat(fileParameters).isNotEmpty();
    assertThat(fileParameters.size()).isEqualTo(1);
    assertThat(fileParameters.get(0)).isEqualToComparingFieldByField(getFileParameters("fileName", "fileContent", 20L));
  }

  // FileStoreNodeDTO is folder
  // folder
  //        - folder1
  //                  - folder2
  //                            - file1
  //                            - file2
  //        - folder3
  //                - file3
  //                - file4
  //        - file5
  //        - file6
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testMapFileNodesWithFolder() {
    FolderNodeDTO folder = getFolder("folder");
    FolderNodeDTO folder1 = getFolder("folder1");
    FolderNodeDTO folder2 = getFolder("folder2");
    FileNodeDTO file1 = getFile("file1Name", "file1Content", 1L);
    FileNodeDTO file2 = getFile("file2Name", "file2Content", 2L);
    folder2.addChild(file1);
    folder2.addChild(file2);
    folder1.addChild(folder2);
    folder.addChild(folder1);

    FolderNodeDTO folder3 = getFolder("folder3");
    FileNodeDTO file3 = getFile("file3Name", "file3Content", 3L);
    FileNodeDTO file4 = getFile("file4Name", "file4Content", 4L);
    folder3.addChild(file3);
    folder3.addChild(file4);
    folder.addChild(folder3);

    FileNodeDTO file5 = getFile("file5Name", "file5Content", 5L);
    FileNodeDTO file6 = getFile("file6Name", "file6Content", 6L);

    folder.addChild(file5);
    folder.addChild(file6);

    List<FileParameters> fileParameters = mapFileNodes(folder,
        fileNode
        -> FileParameters.builder()
               .content(fileNode.getContent())
               .fileName(fileNode.getName())
               .fileSize(fileNode.getSize())
               .build());

    assertThat(fileParameters).isNotNull();
    assertThat(fileParameters).isNotEmpty();
    assertThat(fileParameters.size()).isEqualTo(6);
    assertThat(fileParameters.get(0))
        .isEqualToComparingFieldByField(getFileParameters("file5Name", "file5Content", 5L));
    assertThat(fileParameters.get(1))
        .isEqualToComparingFieldByField(getFileParameters("file6Name", "file6Content", 6L));
    assertThat(fileParameters.get(2))
        .isEqualToComparingFieldByField(getFileParameters("file3Name", "file3Content", 3L));
    assertThat(fileParameters.get(3))
        .isEqualToComparingFieldByField(getFileParameters("file4Name", "file4Content", 4L));
    assertThat(fileParameters.get(4))
        .isEqualToComparingFieldByField(getFileParameters("file1Name", "file1Content", 1L));
    assertThat(fileParameters.get(5))
        .isEqualToComparingFieldByField(getFileParameters("file2Name", "file2Content", 2L));
  }

  private FileNodeDTO getFile(String fileName, String fileContent, long size) {
    return FileStoreNodeDTOMapper.getFileNodeDTO(
        NGFile.builder().name(fileName).type(NGFileType.FILE).size(size).build(), fileContent);
  }

  private FolderNodeDTO getFolder(String folderName) {
    return FileStoreNodeDTOMapper.getFolderNodeDTO(NGFile.builder().name(folderName).type(NGFileType.FOLDER).build());
  }

  private FileParameters getFileParameters(String fileName, String fileContent, long size) {
    return FileParameters.builder().content(fileContent).fileName(fileName).fileSize(size).build();
  }

  @Builder
  private static class FileParameters {
    private String content;
    private String fileName;
    private long fileSize;
  }
}
