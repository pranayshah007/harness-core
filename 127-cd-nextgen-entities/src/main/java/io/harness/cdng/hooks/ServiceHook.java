/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static java.lang.String.format;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.visitor.helpers.hook.ServiceHookVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.exception.UnexpectedTypeException;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.intfc.OverridesApplier;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@SimpleVisitorHelper(helperClass = ServiceHookVisitorHelper.class)
@TypeAlias("serviceHook")
@RecasterAlias("io.harness.cdng.hooks.ServiceHook")
public class ServiceHook implements OverridesApplier<ServiceHook>, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @EntityIdentifier String identifier;

  @NotEmpty @JsonProperty("actions") List<ServiceHookAction> actions;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "Inline")
  @NotNull
  @JsonProperty("storeType")
  StoreConfigType storetype;

  @NotNull
  @JsonTypeInfo(use = NAME, property = "storeType", include = EXTERNAL_PROPERTY, visible = true)
  @Wither
  StoreConfig store;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Value
  public static class ServiceHookParameters {
    String identifier;
    String storeType;
    List<String> actions;
    StoreConfig store;

    public static ServiceHookParameters fromServiceHook(ServiceHook serviceHook) {
      if (serviceHook == null) {
        return null;
      }
      List<String> actions = Collections.emptyList();
      serviceHook.getActions().forEach(action -> actions.add(action.getDisplayName()));
      return new ServiceHookParameters(serviceHook.getIdentifier(),
          serviceHook.getStoretype() == null ? null : serviceHook.getStoretype().getDisplayName(),
          serviceHook.getActions() == null ? null : actions, serviceHook.getStore());
    }
  }

  @Override
  public ServiceHook applyOverrides(ServiceHook overrideConfig) {
    ServiceHook resultantConfig = this;
    if (overrideConfig != null) {
      if (!overrideConfig.getStoretype().equals(resultantConfig.getStoretype())) {
        throw new UnexpectedTypeException(format("Unable to apply store override of type '%s' to store of type '%s'",
            overrideConfig.getStoretype().getDisplayName(), resultantConfig.getStoretype().getDisplayName()));
      }
      resultantConfig = resultantConfig.withStore(store.applyOverrides(overrideConfig.getStore()));
      resultantConfig.setActions(overrideConfig.getActions());
    }
    return resultantConfig;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("store", store);
    return children;
  }
}
