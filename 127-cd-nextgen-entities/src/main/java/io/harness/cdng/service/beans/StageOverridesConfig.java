/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.visitor.helpers.serviceconfig.StageOverridesVisitorHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.NGVariable;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@SimpleVisitorHelper(helperClass = StageOverridesVisitorHelper.class)
@TypeAlias("stageOverridesConfig")
@RecasterAlias("io.harness.cdng.service.beans.StageOverridesConfig")
@OwnedBy(CDC)
public class StageOverridesConfig implements Visitable {
  List<NGVariable> variables;
  ArtifactListConfig artifacts;
  List<ManifestConfigWrapper> manifests;
  List<ConfigFileWrapper> configFiles;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();

    if (EmptyPredicate.isNotEmpty(variables)) {
      variables.forEach(ngVariable -> children.add("variables", ngVariable));
    }
    children.add("artifacts", artifacts);
    if (EmptyPredicate.isNotEmpty(manifests)) {
      manifests.forEach(manifest -> children.add("manifests", manifest));
    }

    if (EmptyPredicate.isNotEmpty(configFiles)) {
      configFiles.forEach(manifest -> children.add("configFiles", configFiles));
    }

    return children;
  }
}
