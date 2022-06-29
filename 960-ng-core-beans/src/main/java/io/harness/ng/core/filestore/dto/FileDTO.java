/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.filestore.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Condition;
import io.harness.data.validator.EntityIdentifier;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.NGFileType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(CDP)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "File", description = "This is details of the file entity defined in Harness.")
@Condition(property = "type", propertyValue = "FILE", requiredProperties = {"fileUsage"},
    message = "FileUsage is required for file.")
@NoArgsConstructor
public class FileDTO {
  @ApiModelProperty(required = true)
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
  private String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) private String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) private String projectIdentifier;

  @ApiModelProperty(required = true)
  @EntityIdentifier
  @Schema(description = "Identifier of the File")
  @FormDataParam("identifier")
  private String identifier;

  @ApiModelProperty(required = true)
  @NotBlank
  @Schema(description = "Name of the File")
  @FormDataParam("name")
  private String name;

  @Schema(description = "This specifies the file usage") @FormDataParam("fileUsage") private FileUsage fileUsage;
  @ApiModelProperty(required = true)
  @NotNull
  @Schema(description = "This specifies the type of the File")
  @FormDataParam("type")
  private NGFileType type;
  @ApiModelProperty(required = true)
  @NotBlank
  @Schema(description = "This specifies parent identifier")
  @FormDataParam("parentIdentifier")
  private String parentIdentifier;
  @Schema(description = "Description of the File") @FormDataParam("description") private String description;
  @Schema(description = "Tags") @Valid private List<NGTag> tags;
  @Schema(description = "Mime type of the File") @FormDataParam("mimeType") private String mimeType;

  // read only properties during serialization(java object -> json)
  @Schema(description = "The path of the File")
  @FormDataParam("path")
  @JsonProperty(access = Access.READ_ONLY)
  private String path;

  @Schema(description = "Whether File is draft or not") @JsonProperty(access = Access.READ_ONLY) private Boolean draft;

  @Schema(description = "File created by user")
  @FormDataParam("createdBy")
  @JsonProperty(access = Access.READ_ONLY)
  private EmbeddedUserDetailsDTO createdBy;

  @Schema(description = "File updated by user")
  @FormDataParam("lastModifiedBy")
  @JsonProperty(access = Access.READ_ONLY)
  private EmbeddedUserDetailsDTO lastModifiedBy;

  @Schema(description = "Last modified time for the File")
  @FormDataParam("lastModifiedAt")
  @JsonProperty(access = Access.READ_ONLY)
  private Long lastModifiedAt;

  @JsonIgnore
  public boolean isFile() {
    return type == NGFileType.FILE;
  }

  @JsonIgnore
  public boolean isFolder() {
    return type == NGFileType.FOLDER;
  }

  @JsonIgnore
  public boolean isDraft() {
    return draft != null && draft;
  }

  @Builder
  public FileDTO(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier,
      String name, String path, FileUsage fileUsage, NGFileType type, String parentIdentifier, String description,
      List<NGTag> tags, String mimeType, Boolean draft, EmbeddedUserDetailsDTO createdBy,
      EmbeddedUserDetailsDTO lastModifiedBy, Long lastModifiedAt) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.identifier = identifier;
    this.name = name;
    this.path = path;
    this.fileUsage = fileUsage;
    this.type = type;
    this.parentIdentifier = parentIdentifier;
    this.description = description;
    this.tags = tags;
    this.mimeType = mimeType;
    this.draft = draft;
    this.createdBy = createdBy;
    this.lastModifiedBy = lastModifiedBy;
    this.lastModifiedAt = lastModifiedAt;
  }
}
