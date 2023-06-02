/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleAssignmentDeleteResponseDTO {
  @Schema(description = "Number of roleassignings that are successfully deleted.") int successfullyDeleted;
  @Schema(description = "Number of roleassignings that are not deleted") int failedToDelete;

  @Schema(description = "List of roleassignments along with error message that are not deleted.")
  List<RoleAssignmentErrorResponseDTO> roleAssignmentErrorResponseDTOList;
}
