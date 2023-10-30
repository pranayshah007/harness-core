/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.NGAccess;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(DX)
public interface GitSyncableEntity extends NGAccess {
  String getUuid();

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default String getObjectIdOfYaml() {
    return null;
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default void setObjectIdOfYaml(String objectIdOfYaml) {
    // Do nothing; this method is deprecated
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default Boolean getIsFromDefaultBranch() {
    return false;
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default void setIsFromDefaultBranch(Boolean isFromDefaultBranch) {
    // Do nothing; this method is deprecated
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default void setBranch(String branch) {
    // Do nothing; this method is deprecated
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default String getBranch() {
    return null;
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default String getYamlGitConfigRef() {
    return null;
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default void setYamlGitConfigRef(String yamlGitConfigRef) {
    // Do nothing; this method is deprecated
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default String getRootFolder() {
    return null;
  }

  /**
   * @deprecated This method is deprecated for new git experience
   */
  @Deprecated(forRemoval = false)
  default void setRootFolder(String rootFolder) {
    // Do nothing; this method is deprecated
  }

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
