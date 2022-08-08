/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.filestore.dto;

import static io.harness.NGResourceFilterConstants.IDENTIFIER_LIST;
import static io.harness.NGResourceFilterConstants.SEARCH_TERM;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "FileFilter", description = "This is the File Filter defined in Harness.")
public class FileFilterDTO {
  @Schema(description = SEARCH_TERM) String searchTerm;
  @Schema(description = IDENTIFIER_LIST) List<String> identifiers;
}
