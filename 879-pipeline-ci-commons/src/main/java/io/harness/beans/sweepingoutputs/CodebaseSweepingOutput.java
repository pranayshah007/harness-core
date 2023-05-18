/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.sweepingoutputs;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(HarnessTeam.CI)
@TypeAlias(CODEBASE)
@JsonTypeName(CODEBASE)
@RecasterAlias("io.harness.beans.sweepingoutputs.CodebaseSweepingOutput")
public class CodebaseSweepingOutput implements ExecutionSweepingOutput {
  String branch;
  String tag;
  String targetBranch;
  String sourceBranch;
  String prNumber;
  String prTitle;
  String commitSha;
  String shortCommitSha;
  String baseCommitSha;
  String commitRef;
  String repoUrl;
  String gitUserId;
  String gitUserEmail;
  String gitUser;
  String gitUserAvatar;
  String pullRequestLink;
  String pullRequestBody;
  String state;
  Build build;
  List<CodeBaseCommit> commits;
  String mergeSha;
  String commitMessage;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore String uuid;

  @Value
  @Builder
  public static class CodeBaseCommit {
    String id;
    String link;
    String message;
    String ownerName;
    String ownerId;
    String ownerEmail;
    long timeStamp;
  }
}
