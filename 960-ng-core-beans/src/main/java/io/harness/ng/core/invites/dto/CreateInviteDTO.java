/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.invites.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.invites.InviteType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(value = "CreateInvite")
@OwnedBy(PL)
@Schema(name = "CreateInvite", description = "This is the view of the CreateInvite entity defined in Harness")
public class CreateInviteDTO {
  @Schema(description = "List of users to be invited.")
  @ApiModelProperty(required = true)
  @NotEmpty
  @Size(max = 100)
  List<String> users;
  @Schema(description = "Role bindings to be associated with the invited users.") List<RoleBinding> roleBindings;
  @Schema(description = "Specifies the invite type.") @ApiModelProperty(required = true) InviteType inviteType;
  @Schema(description = "List of the user Groups in the invite.") List<String> userGroups;
}
