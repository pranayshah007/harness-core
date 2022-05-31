/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.dto.filter;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.filestore.FileUsage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("io.harness.ng.core.dto.filestore.filter.FilesFilterProperties")
@OwnedBy(CDP)
public class FilesFilterProperties extends FilterProperties {
  private FileUsage fileUsage;
  private EmbeddedUserDetailsDTO createdBy;
  private EntityDetail referencedBy;
}
