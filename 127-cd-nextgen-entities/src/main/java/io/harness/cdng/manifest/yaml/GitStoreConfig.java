/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.summary.ManifestStoreInfo.ManifestStoreInfoBuilder;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.pms.yaml.ParameterField;

import java.util.List;

public interface GitStoreConfig extends StoreConfig {
  ParameterField<String> getConnectorRef();
  FetchType getGitFetchType();
  ParameterField<String> getBranch();
  ParameterField<String> getCommitId();
  ParameterField<List<String>> getPaths();
  ParameterField<String> getFolderPath();
  ParameterField<String> getRepoName();
  GitStoreConfigDTO toGitStoreConfigDTO();
  @Override
  default void populateManifestStoreInfo(ManifestStoreInfoBuilder manifestStoreInfoBuilder) {
    manifestStoreInfoBuilder.branch(getParameterFieldValue(this.getBranch()));
    manifestStoreInfoBuilder.commitId(getParameterFieldValue(this.getCommitId()));
    manifestStoreInfoBuilder.folderPath(getParameterFieldValue(this.getFolderPath()));
    manifestStoreInfoBuilder.repoName(getParameterFieldValue(this.getRepoName()));
    manifestStoreInfoBuilder.paths(getParameterFieldValue(this.getPaths()));
  }

  @Override
  default List<String> retrieveFilePaths() {
    return this.getPaths().getValue();
  }
}
