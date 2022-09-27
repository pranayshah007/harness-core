/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.sdk;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class GitXNewApiConstants {
  public static final String BRANCH_KEY = "branch_name";
  public static final String FILE_PATH_KEY = "file_path";
  public static final String COMMIT_MSG_KEY = "commit_message";
  public static final String NEW_BRANCH_KEY = "create_new_branch";
  public static final String LAST_OBJECT_ID_KEY = "last_object_id";

  public static final String BASE_BRANCH_KEY = "base_branch";
  public static final String CONNECTOR_REF_KEY = "connector_ref";
  public static final String STORE_TYPE_KEY = "store_type";
  public static final String REPO_KEY = "repo_name";
  public static final String LAST_COMMIT_ID_KEY = "last_commit_id";
}
