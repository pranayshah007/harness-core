/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import java.util.HashMap;
import java.util.Map;

public class ServiceInstanceArtifactParam implements ContextElement {
  public static final String SERVICE_INSTANCE_ARTIFACT_PARAMS = "SERVICE_INSTANCE_ARTIFACT_PARAMS";

  private Map<String, String> instanceArtifactMap = new HashMap<>();

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.PARAM;
  }

  @Override
  public String getUuid() {
    return null;
  }

  @Override
  public String getName() {
    return SERVICE_INSTANCE_ARTIFACT_PARAMS;
  }

  public Map<String, String> getInstanceArtifactMap() {
    return instanceArtifactMap;
  }

  public void setInstanceArtifactMap(Map<String, String> instanceArtifactMap) {
    this.instanceArtifactMap = instanceArtifactMap;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
