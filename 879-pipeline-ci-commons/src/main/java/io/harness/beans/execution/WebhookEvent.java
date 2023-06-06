/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = BranchWebhookEvent.class, name = "BRANCH")
  , @JsonSubTypes.Type(value = PRWebhookEvent.class, name = "PR"),
      @JsonSubTypes.Type(value = ReleaseWebhookEvent.class, name = "RELEASE")
})
public interface WebhookEvent {
  enum Type { PR, BRANCH, RELEASE }
  WebhookEvent.Type getType();
}
