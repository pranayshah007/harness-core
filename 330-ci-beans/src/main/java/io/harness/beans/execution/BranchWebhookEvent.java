/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution;

import static io.harness.beans.execution.WebhookEvent.Type.BRANCH;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName("Branch")
@TypeAlias("branchWebhookEvent")
@RecasterAlias("io.harness.beans.execution.BranchWebhookEvent")
public class BranchWebhookEvent implements WebhookEvent {
  private String branchName;
  private String link;
  private List<CommitDetails> commitDetailsList;
  private Repository repository;
  private WebhookBaseAttributes baseAttributes;

  @Override
  public Type getType() {
    return BRANCH;
  }
}
