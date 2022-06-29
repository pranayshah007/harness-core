/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContextData;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(PL)
@TypeAlias("ScmGitMetaDataContext")
public class ScmGitMetaDataContext implements GlobalContextData {
  public static final String NG_GIT_SYNC_CONTEXT = "SCM_GIT_META_DATA_CONTEXT";

  @Wither ScmGitMetaData scmGitMetaData;

  @Override
  public String getKey() {
    return NG_GIT_SYNC_CONTEXT;
  }
}
