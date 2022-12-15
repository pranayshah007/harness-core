/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler;

import io.harness.ff.FeatureFlagService;

import software.wings.beans.AllowedValueYaml;
import software.wings.beans.ServiceVariable;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ServiceVariableYamlHelper {
  @Inject FeatureFlagService featureFlagService;
  @Inject ArtifactStreamService artifactStreamService;
  @Inject SettingsService settingsService;

  public void convertArtifactVariableToYaml(
      String accountId, ServiceVariable serviceVariable, List<AllowedValueYaml> allowedValueYamlList) {
    log.warn("Variable type ARTIFACT not supported, skipping processing of variable {}", serviceVariable.getName());
  }
}
