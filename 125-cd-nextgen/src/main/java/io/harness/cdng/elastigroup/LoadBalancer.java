/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.harness.annotation.RecasterAlias;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.infra.beans.host.HostFilterSpec;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.exception.UnexpectedTypeException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.intfc.OverridesApplier;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static java.lang.String.format;

@Data
@Builder
@NoArgsConstructor
@TypeAlias("LoadBalancer")
@RecasterAlias("io.harness.cdng.elastigroup.LoadBalancer")
public class LoadBalancer {

  @NotNull @JsonProperty("type") LoadBalancerType type;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  LoadBalancerSpec spec;

//  @Override
//  public VisitableChildren getChildrenToWalk() {
//    VisitableChildren children = VisitableChildren.builder().build();
//    children.add(YAMLFieldNameConstants.SPEC, spec);
//    return children;
//  }

//  @Override
//  public LoadBalancer applyOverrides(LoadBalancer loadBalancer) {
//    LoadBalancer resultantLoadBalancer = this;
//    if (loadBalancer != null) {
//      if (!loadBalancer.getType().equals(resultantLoadBalancer.getType())) {
//        throw new UnexpectedTypeException(format("Unable to apply load balancer override of type '%s' to load balancer of type '%s'",
//                loadBalancer.getType().getDisplayName(), resultantLoadBalancer.getType().getDisplayName()));
//      }
//
//      resultantLoadBalancer = resultantLoadBalancer.withSpec(spec.applyOverrides(loadBalancer.getSpec()));
//    }
//    return resultantLoadBalancer;
//  }

  @Builder
  public LoadBalancer(LoadBalancerType type, LoadBalancerSpec spec) {
    this.type = type;
    this.spec = spec;
  }
}
