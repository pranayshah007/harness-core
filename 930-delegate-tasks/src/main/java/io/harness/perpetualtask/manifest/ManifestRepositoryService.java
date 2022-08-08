/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.manifest;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;

import software.wings.beans.appmanifest.HelmChart;

import java.util.List;
import javax.validation.constraints.NotNull;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public interface ManifestRepositoryService {
  List<HelmChart> collectManifests(@NotNull ManifestCollectionParams params) throws Exception;

  void cleanup(@NotNull ManifestCollectionParams params) throws Exception;
}
