/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.NGAccess;

@OwnedBy(DX)
public interface GitSyncableEntity extends NGAccess {
  String getUuid();

  String getObjectIdOfYaml();

  void setObjectIdOfYaml(String objectIdOfYaml);

  Boolean getIsFromDefaultBranch();

  void setIsFromDefaultBranch(Boolean isFromDefaultBranch);

  void setBranch(String branch);

  String getBranch();

  String getYamlGitConfigRef();

  void setYamlGitConfigRef(String yamlGitConfigRef);

  String getRootFolder();

  void setRootFolder(String rootFolder);

  String getFilePath();

  void setFilePath(String filePath);

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default boolean isEntityInvalid() {
    return false;
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default void setEntityInvalid(boolean isEntityInvalid) {
    // Do nothing; this method is deprecated
  }

  String getInvalidYamlString();
}
