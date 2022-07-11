/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AzureRepoStoreDTO implements GitStoreConfigDTO {
  String connectorRef;

  FetchType gitFetchType;
  String branch;
  String commitId;

  List<String> paths;
  String folderPath;
  String repoName;

  @Override
  public AzureRepoStore toGitStoreConfig() {
    return AzureRepoStore.builder()
        .branch(ParameterField.createValueField(branch))
        .commitId(ParameterField.createValueField(commitId))
        .connectorRef(ParameterField.createValueField(connectorRef))
        .folderPath(ParameterField.createValueField(folderPath))
        .gitFetchType(gitFetchType)
        .paths(ParameterField.createValueField(paths))
        .repoName(ParameterField.createValueField(repoName))
        .build();
  }
}
