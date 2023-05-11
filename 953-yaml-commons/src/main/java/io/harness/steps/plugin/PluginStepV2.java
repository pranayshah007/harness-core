/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin;

import io.harness.yaml.extended.ci.container.ContainerResource;
import io.harness.yaml.extended.ci.container.ImageDetails;

import java.util.Map;

public interface PluginStepV2 {
  ContainerResource getResources();

  ImageDetails getImageDetails();

  Integer getRunAsUser();

  boolean getPrivileged();

  String getIdentifier();

  String getUuid();

  String getType();

  Map<String, String> getEnvVariables();
}
