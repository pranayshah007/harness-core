/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("webhookBaseAttributes")
@RecasterAlias("io.harness.beans.execution.WebhookBaseAttributes")
public class WebhookBaseAttributes {
  private String message;
  private String link;
  private String before;
  private String after;
  private String ref;
  private String source;
  private String target;
  private String authorLogin;
  private String authorName;
  private String authorEmail;
  private String authorAvatar;
  private String sender;
  private String action;
  private String mergeSha;
}
