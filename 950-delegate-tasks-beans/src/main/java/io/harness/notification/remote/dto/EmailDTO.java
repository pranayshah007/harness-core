/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import com.google.inject.Inject;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class EmailDTO {
  @NotNull String accountId;
  @NotNull Set<String> ccRecipients;
  @NotNull Set<String> toRecipients;
  @NotNull String notificationId;
  @NotNull String subject;
  @NotNull String body;
  @Setter boolean sendToNonHarnessRecipients;
}
