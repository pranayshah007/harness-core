/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
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
}
