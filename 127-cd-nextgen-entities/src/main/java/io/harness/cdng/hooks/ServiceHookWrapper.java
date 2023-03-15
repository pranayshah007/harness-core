/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.helpers.hook.ServiceHookWrapperVisitorHelper;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfField;
import io.harness.walktree.beans.VisitableChild;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@SimpleVisitorHelper(helperClass = ServiceHookWrapperVisitorHelper.class)
@TypeAlias("serviceHookWrapper")
@RecasterAlias("io.harness.cdng.hooks.ServiceHookWrapper")
@OneOfField(fields = {"preHook", "postHook"})
@OwnedBy(HarnessTeam.CDP)
public class ServiceHookWrapper implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  ServiceHook preHook;
  ServiceHook postHook;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    List<VisitableChild> children = new ArrayList<>();
    if (preHook != null) {
      children.add(VisitableChild.builder().value(preHook).fieldName("preHook").build());
    }
    if (postHook != null) {
      children.add(VisitableChild.builder().value(postHook).fieldName("postHook").build());
    }
    return VisitableChildren.builder().visitableChildList(children).build();
  }
}
