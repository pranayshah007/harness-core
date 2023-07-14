/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.gcr;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.util.List;
import java.util.Map;

/**
 * The type GCR image tag response.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class GcrImageTagResponse {
  private List<String> child;
  private String name;
  private List<String> tags;
  private Map manifest;

  public Map getManifest() {
    return manifest;
  }

  public void setManifest(Map manifest) {
    this.manifest = manifest;
  }

  public List<String> getChild() {
    return child;
  }

  public void setChild(List<String> child) {
    this.child = child;
  }

  /**
   * Gets name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets tags.
   *
   * @return the tags
   */
  public List<String> getTags() {
    return tags;
  }

  /**
   * Sets tags.
   *
   * @param tags the tags
   */
  public void setTags(List<String> tags) {
    this.tags = tags;
  }
}
