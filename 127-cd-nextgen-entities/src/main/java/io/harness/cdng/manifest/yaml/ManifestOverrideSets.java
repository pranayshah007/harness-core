/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestConfig.ManifestConfigStepParameters;
import io.harness.cdng.visitor.helpers.manifest.ManifestOverridesVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("overrideSet")
@SimpleVisitorHelper(helperClass = ManifestOverridesVisitorHelper.class)
@TypeAlias("manifestOverrideSets")
public class ManifestOverrideSets implements Visitable {
  @EntityIdentifier String identifier;
  List<ManifestConfigWrapper> manifests;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    manifests.forEach(manifest -> children.add("manifests", manifest));
    return children;
  }

  @Value
  public static class ManifestOverrideSetsStepParametersWrapper {
    ManifestOverrideSetsStepParameters manifests;

    public static ManifestOverrideSetsStepParametersWrapper fromManifestOverrideSets(
        ManifestOverrideSets manifestOverrideSets) {
      return new ManifestOverrideSetsStepParametersWrapper(
          ManifestOverrideSetsStepParameters.fromManifestOverrideSets(manifestOverrideSets));
    }
  }

  public static class ManifestOverrideSetsStepParameters extends HashMap<String, ManifestConfigStepParameters> {
    public static ManifestOverrideSetsStepParameters fromManifestOverrideSets(
        ManifestOverrideSets manifestOverrideSets) {
      if (manifestOverrideSets == null || manifestOverrideSets.getManifests() == null) {
        return null;
      }
      ManifestOverrideSetsStepParameters stepParameters = new ManifestOverrideSetsStepParameters();
      manifestOverrideSets.getManifests().forEach(m
          -> stepParameters.put(
              m.getManifest().getIdentifier(), ManifestConfigStepParameters.fromManifestConfig(m.getManifest())));
      return stepParameters;
    }
  }
}
