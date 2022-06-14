/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.dto.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.dto.mapper.EmbeddedUserDTOMapper.fromEmbeddedUser;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileNodeDTO.FileNodeDTOBuilder;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.entities.NGFile;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@UtilityClass
public class FileStoreNodeDTOMapper {
  public FileNodeDTO getFileNodeDTO(NGFile ngFile, final String content) {
    FileNodeDTOBuilder fileNodeDTOBuilder = FileNodeDTO.builder()
                                                .identifier(ngFile.getIdentifier())
                                                .parentIdentifier(ngFile.getParentIdentifier())
                                                .name(ngFile.getName())
                                                .path(ngFile.getPath())
                                                .fileUsage(ngFile.getFileUsage())
                                                .description(ngFile.getDescription())
                                                .tags(ngFile.getTags())
                                                .lastModifiedAt(ngFile.getLastModifiedAt())
                                                .lastModifiedBy(fromEmbeddedUser(ngFile.getLastUpdatedBy()))
                                                .mimeType(ngFile.getMimeType());

    if (ngFile.getSize() != null) {
      fileNodeDTOBuilder.size(ngFile.getSize());
    }

    if (StringUtils.isNotBlank(content)) {
      fileNodeDTOBuilder.content(content);
    }

    return fileNodeDTOBuilder.build();
  }

  public FolderNodeDTO getFolderNodeDTO(NGFile ngFile) {
    return FolderNodeDTO.builder()
        .identifier(ngFile.getIdentifier())
        .parentIdentifier(ngFile.getParentIdentifier())
        .name(ngFile.getName())
        .path(ngFile.getPath())
        .lastModifiedAt(ngFile.getLastModifiedAt())
        .lastModifiedBy(fromEmbeddedUser(ngFile.getLastUpdatedBy()))
        .build();
  }
}
