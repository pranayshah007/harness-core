/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.beans;

import io.harness.cvng.notification.channelDetails.CVNGNotificationChannel;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "NotificationRule", description = "This is the Notification Rule entity defined in Harness")
public class NotificationRuleDTO implements YamlDTO {
  @EntityIdentifier(allowBlank = true) String orgIdentifier;
  @EntityIdentifier(allowBlank = true) String projectIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String identifier;
  @ApiModelProperty(required = true) @NotNull @NGEntityName String name;

  @NotNull NotificationRuleType type;
  @Valid @NotNull List<NotificationRuleCondition> conditions;
  @Valid @NotNull CVNGNotificationChannel notificationMethod;
}
