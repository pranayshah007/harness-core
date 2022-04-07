/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.filestore.FileDTO;
import io.harness.ng.core.entities.NGFile;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class FileDTOMapper {
  public NGFile getNGFileFromDTO(FileDTO fileDto) {
    if (fileDto.isFolder()) {
      return NGFile.builder()
          .accountIdentifier(fileDto.getAccountIdentifier())
          .orgIdentifier(fileDto.getOrgIdentifier())
          .projectIdentifier(fileDto.getProjectIdentifier())
          .identifier(fileDto.getIdentifier())
          .parentIdentifier(fileDto.getParentIdentifier())
          .fileName(fileDto.getName())
          .type(fileDto.getType())
          .build();
    }

    return NGFile.builder()
        .accountIdentifier(fileDto.getAccountIdentifier())
        .orgIdentifier(fileDto.getOrgIdentifier())
        .projectIdentifier(fileDto.getProjectIdentifier())
        .identifier(fileDto.getIdentifier())
        .fileName(fileDto.getName())
        .fileUsage(fileDto.getFileUsage())
        .type(fileDto.getType())
        .parentIdentifier(fileDto.getParentIdentifier())
        .description(fileDto.getDescription())
        .tags(!EmptyPredicate.isEmpty(fileDto.getTags()) ? fileDto.getTags() : Collections.emptyList())
        .entityType(fileDto.getEntityType())
        .entityId(fileDto.getEntityId())
        .mimeType(fileDto.getMimeType())
        .build();
  }

  public FileDTO getFileDTOFromNGFile(NGFile ngFile) {
    if (ngFile.isFolder()) {
      return FileDTO.builder()
          .accountIdentifier(ngFile.getAccountIdentifier())
          .orgIdentifier(ngFile.getOrgIdentifier())
          .projectIdentifier(ngFile.getProjectIdentifier())
          .identifier(ngFile.getIdentifier())
          .name(ngFile.getFileName())
          .type(ngFile.getType())
          .parentIdentifier(ngFile.getParentIdentifier())
          .build();
    }

    return FileDTO.builder()
        .accountIdentifier(ngFile.getAccountIdentifier())
        .orgIdentifier(ngFile.getOrgIdentifier())
        .projectIdentifier(ngFile.getProjectIdentifier())
        .identifier(ngFile.getIdentifier())
        .name(ngFile.getFileName())
        .fileUsage(ngFile.getFileUsage())
        .type(ngFile.getType())
        .parentIdentifier(ngFile.getParentIdentifier())
        .description(ngFile.getDescription())
        .tags(ngFile.getTags())
        .entityType(ngFile.getEntityType())
        .entityId(ngFile.getEntityId())
        .mimeType(ngFile.getMimeType())
        .build();
  }

  public NGFile updateNGFile(FileDTO fileDto, NGFile file) {
    file.setFileUsage(fileDto.getFileUsage());
    file.setType(fileDto.getType());
    file.setParentIdentifier(fileDto.getParentIdentifier());
    file.setDescription(fileDto.getDescription());
    file.setTags(!EmptyPredicate.isEmpty(fileDto.getTags()) ? fileDto.getTags() : Collections.emptyList());
    file.setEntityId(fileDto.getEntityId());
    file.setEntityType(fileDto.getEntityType());
    file.setFileName(fileDto.getName());
    file.setMimeType(fileDto.getMimeType());
    return file;
  }
}
