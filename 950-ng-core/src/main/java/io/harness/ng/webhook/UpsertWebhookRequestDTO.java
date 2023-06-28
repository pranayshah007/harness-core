/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HookEventType;
import io.harness.validation.OneOfField;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "UpsertWebhookRequestDTOKeys")
@OwnedBy(DX)
@Schema(name = "UpsertWebhookRequest",
    description = "This is the view of the UpsertWebhookRequest entity defined in Harness")
@OneOfField(fields = {"connectorIdentifierRef", "isHarnessCode"})
public class UpsertWebhookRequestDTO {
  @NotNull @NotEmpty String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String connectorIdentifierRef;
  @NotNull HookEventType hookEventType;
  String repoURL;
  String webhookSecretIdentifierRef;
  Boolean isHarnessCode;
}
