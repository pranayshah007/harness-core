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
import io.harness.data.structure.EmptyPredicate;
import io.harness.file.beans.NGBaseFile;
import io.harness.filestore.entities.NGFile;
import io.harness.ng.core.filestore.dto.FileDTO;

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
          .name(fileDto.getName())
          .path(fileDto.getPath())
          .type(fileDto.getType())
          .build();
    }

    return NGFile.builder()
        .accountIdentifier(fileDto.getAccountIdentifier())
        .orgIdentifier(fileDto.getOrgIdentifier())
        .projectIdentifier(fileDto.getProjectIdentifier())
        .identifier(fileDto.getIdentifier())
        .name(fileDto.getName())
        .path(fileDto.getPath())
        .fileUsage(fileDto.getFileUsage())
        .type(fileDto.getType())
        .parentIdentifier(fileDto.getParentIdentifier())
        .description(fileDto.getDescription())
        .tags(!EmptyPredicate.isEmpty(fileDto.getTags()) ? fileDto.getTags() : Collections.emptyList())
        .mimeType(fileDto.getMimeType())
        .draft(fileDto.getDraft())
        .build();
  }

  public FileDTO getFileDTOFromNGFile(NGFile ngFile) {
    if (ngFile.isFolder()) {
      return FileDTO.builder()
          .accountIdentifier(ngFile.getAccountIdentifier())
          .orgIdentifier(ngFile.getOrgIdentifier())
          .projectIdentifier(ngFile.getProjectIdentifier())
          .identifier(ngFile.getIdentifier())
          .name(ngFile.getName())
          .path(ngFile.getPath())
          .type(ngFile.getType())
          .parentIdentifier(ngFile.getParentIdentifier())
          .createdBy(fromEmbeddedUser(ngFile.getCreatedBy()))
          .lastModifiedBy(fromEmbeddedUser(ngFile.getLastUpdatedBy()))
          .lastModifiedAt(ngFile.getLastModifiedAt())
          .build();
    }

    return FileDTO.builder()
        .accountIdentifier(ngFile.getAccountIdentifier())
        .orgIdentifier(ngFile.getOrgIdentifier())
        .projectIdentifier(ngFile.getProjectIdentifier())
        .identifier(ngFile.getIdentifier())
        .name(ngFile.getName())
        .path(ngFile.getPath())
        .fileUsage(ngFile.getFileUsage())
        .type(ngFile.getType())
        .parentIdentifier(ngFile.getParentIdentifier())
        .description(ngFile.getDescription())
        .tags(ngFile.getTags())
        .mimeType(ngFile.getMimeType())
        .draft(ngFile.getDraft())
        .createdBy(fromEmbeddedUser(ngFile.getCreatedBy()))
        .lastModifiedBy(fromEmbeddedUser(ngFile.getLastUpdatedBy()))
        .lastModifiedAt(ngFile.getLastModifiedAt())
        .build();
  }

  public NGFile updateNGFile(FileDTO fileDto, NGFile file) {
    if (fileDto.isFolder()) {
      file.setType(fileDto.getType());
      file.setParentIdentifier(fileDto.getParentIdentifier());
      file.setName(fileDto.getName());
      file.setPath(fileDto.getPath());
      return file;
    }

    file.setFileUsage(fileDto.getFileUsage());
    file.setType(fileDto.getType());
    file.setParentIdentifier(fileDto.getParentIdentifier());
    file.setDescription(fileDto.getDescription());
    file.setTags(!EmptyPredicate.isEmpty(fileDto.getTags()) ? fileDto.getTags() : Collections.emptyList());
    file.setName(fileDto.getName());
    file.setPath(fileDto.getPath());
    file.setMimeType(fileDto.getMimeType());
    file.setDraft(fileDto.getDraft());
    return file;
  }

  public NGBaseFile getNgBaseFileFromFileDTO(FileDTO fileDto) {
    NGBaseFile baseFile = new NGBaseFile();
    baseFile.setFileName(fileDto.getName());
    baseFile.setMimeType(fileDto.getMimeType());
    baseFile.setAccountId(fileDto.getAccountIdentifier());
    return baseFile;
  }
}
