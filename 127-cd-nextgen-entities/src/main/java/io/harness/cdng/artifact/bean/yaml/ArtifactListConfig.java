/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.helpers.artifact.ArtifactListConfigVisitorHelper;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Singular;
import org.springframework.data.annotation.TypeAlias;

/**
 * This is Yaml POJO class which may contain expressions as well.
 * Used mainly for converter layer to store yaml.
 */
@OwnedBy(HarnessTeam.CDC)
@Data
@SimpleVisitorHelper(helperClass = ArtifactListConfigVisitorHelper.class)
@TypeAlias("artifactListConfig")
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.ArtifactListConfig")
public class ArtifactListConfig implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  PrimaryArtifact primary;
  List<SidecarArtifactWrapper> sidecars;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder
  @ConstructorProperties({"uuid", "primary", "sidecars", "metadata"})
  public ArtifactListConfig(
      String uuid, PrimaryArtifact primary, @Singular List<SidecarArtifactWrapper> sidecars, String metadata) {
    this.uuid = uuid;
    this.primary = primary;
    if (primary != null) {
      this.primary.getSpec().setIdentifier("primary");
      this.primary.getSpec().setPrimaryArtifact(true);
    }
    this.sidecars = sidecars;
    if (isNotEmpty(sidecars)) {
      for (SidecarArtifactWrapper sidecar : this.sidecars) {
        sidecar.getSidecar().getSpec().setIdentifier(sidecar.getSidecar().getIdentifier());
        sidecar.getSidecar().getSpec().setPrimaryArtifact(false);
      }
    }
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("primary", primary);
    if (isNotEmpty(sidecars)) {
      sidecars.forEach(sidecar -> children.add("sidecars", sidecar));
    }
    return children;
  }
}
