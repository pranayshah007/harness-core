package io.harness.gitsync.common.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ScmGetBranchHeadCommitResponseDTO {
  String commitId;
  String message;
  String commitLink;
}
