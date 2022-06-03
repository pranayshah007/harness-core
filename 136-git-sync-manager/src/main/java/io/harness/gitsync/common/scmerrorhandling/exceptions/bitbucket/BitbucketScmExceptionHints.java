package io.harness.gitsync.common.scmerrorhandling.exceptions.bitbucket;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class BitbucketScmExceptionHints {
  public static final String INVALID_CREDENTIALS = "Please check your bitbucket credentials.";
  public static final String REPO_NOT_FOUND = "Please check your bitbucket repository.";
}
