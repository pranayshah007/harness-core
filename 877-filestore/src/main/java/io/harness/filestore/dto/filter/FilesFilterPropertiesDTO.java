/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.dto.filter;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filter.FilterConstants.FILE_STORE_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.filestore.FileUsage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(FILE_STORE_FILTER)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("FilesFilterProperties")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class FilesFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = "This specifies the file usage") FileUsage fileUsage;
  @Schema(description = "File created by user") EmbeddedUserDetailsDTO createdBy;
  @Schema(description = "File referenced by other entity") ReferencedByDTO referencedBy;

  @Override
  public FilterType getFilterType() {
    return FilterType.FILESTORE;
  }
}
