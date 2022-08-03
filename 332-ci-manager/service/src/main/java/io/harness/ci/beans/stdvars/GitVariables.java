/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.stdvars;

import io.harness.validation.Update;

import com.github.reinert.jjschema.SchemaIgnore;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.mongodb.morphia.annotations.Id;

@Value
@Builder
public class GitVariables {
  private String tag;
  private String revision;
  private String targetRepo;
  private String sourceRepo;
  private String targetBranch;
  private String sourceBranch;
  private boolean isPullRequest;
  private boolean pullRequestID;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
}
