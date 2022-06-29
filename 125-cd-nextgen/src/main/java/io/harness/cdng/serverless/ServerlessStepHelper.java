/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessDeployResult;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.git.model.FetchFilesResult;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDP)
public interface ServerlessStepHelper {
  ManifestOutcome getServerlessManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes);

  String getConfigOverridePath(ManifestOutcome manifestOutcome);

  ServerlessDeployConfig getServerlessDeployConfig(ServerlessSpecParameters serverlessSpecParameters);

  ServerlessManifestConfig getServerlessManifestConfig(
      ManifestOutcome manifestOutcome, Ambiance ambiance, Map<String, Object> manifestParams);

  List<ServerInstanceInfo> getServerlessDeployFunctionInstanceInfo(
      ServerlessDeployResult serverlessDeployResult, String infraStructureKey);

  Optional<Pair<String, String>> getManifestFileContent(
      Map<String, FetchFilesResult> fetchFilesResultMap, ManifestOutcome manifestOutcome);
}
