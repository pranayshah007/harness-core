package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum GitOperation {
  CREATE_FILE,
  UPDATE_FILE,
  GET_FILE;
}
