/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution;

import static io.harness.beans.execution.WebhookEvent.Type.RELEASE;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName("RELEASE")
@TypeAlias("releaseWebhookEvent")
@RecasterAlias("io.harness.beans.execution.ReleaseWebhookEvent")
public class ReleaseWebhookEvent implements WebhookEvent {
  private String releaseTag;
  private String releaseLink;
  private String releaseBody;
  private String title;
  private boolean draft;
  private boolean prerelease;
  private Repository repository;
  private WebhookBaseAttributes baseAttributes;

  @Override
  public Type getType() {
    return RELEASE;
  }
}
