/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.dto.node;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.NGFileType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
@Schema(name = "FileNode", description = "This contains file details")
public final class FileNodeDTO extends FileStoreNodeDTO {
  @NotNull @Schema(description = "File usage of the File Store Node") private FileUsage fileUsage;
  @Schema(description = "Description of the File Store Node") private String description;
  @Schema(description = "Tags of the File Store Node") private List<NGTag> tags;
  @Schema(description = "Mime type of the File Store Node") private String mimeType;
  @Schema(description = "Content of the file") private String content;
  @Schema(description = "The size of the file") private long size;

  @Builder
  public FileNodeDTO(String identifier, String parentIdentifier, String name, String path, Long lastModifiedAt,
      EmbeddedUserDetailsDTO lastModifiedBy, FileUsage fileUsage, String description, List<NGTag> tags, String mimeType,
      String content, long size) {
    super(NGFileType.FILE, identifier, parentIdentifier, name, path, lastModifiedAt, lastModifiedBy);
    this.fileUsage = fileUsage;
    this.description = description;
    this.tags = tags;
    this.mimeType = mimeType;
    this.content = content;
    this.size = size;
  }
}
