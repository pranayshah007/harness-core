/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class EmailDTO {
  @NotNull String accountId;
  @NotNull List<String> recipients;
  @NotNull List<String> ccRecipients;
  @NotNull String notificationId;
  @NotNull String subject;
  @NotNull String body;
}
