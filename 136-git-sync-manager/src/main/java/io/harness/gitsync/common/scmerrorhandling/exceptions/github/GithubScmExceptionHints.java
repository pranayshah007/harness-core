package io.harness.gitsync.common.scmerrorhandling.exceptions.github;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GithubScmExceptionHints {
  public static final String INVALID_CREDENTIALS = "Please check your github credentials.";
  public static final String REPO_NOT_FOUND = "Please check your github repository.";
}
