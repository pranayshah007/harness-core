/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.core.beans.dependency.ServiceDependencyMetadata;
import io.harness.cvng.core.beans.template.TemplateDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonitoredServiceDTO {
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String orgIdentifier;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String projectIdentifier;
  @ApiModelProperty(required = true) @NotNull String identifier;
  @ApiModelProperty(required = true) @NotNull @NGEntityName String name;
  @ApiModelProperty(required = true) @NotNull MonitoredServiceType type;
  String description;
  @ApiModelProperty(required = true) @NotNull @EntityIdentifier String serviceRef;
  @EntityIdentifier String environmentRef;
  List<String> environmentRefList;
  @ApiModelProperty(required = true) @NotNull @Size(max = 128) Map<String, String> tags;
  @Valid Sources sources;
  @Valid Set<ServiceDependencyDTO> dependencies;
  List<NotificationRuleRefDTO> notificationRuleRefs;
  @Valid TemplateDTO template;

  public List<String> getEnvironmentRefList() {
    // For migration. Remove once envRefList is populated from UI.
    if (isEmpty(environmentRefList)) {
      return Collections.singletonList(environmentRef);
    }
    return environmentRefList;
  }

  public String getEnvironmentRef() {
    // For migration so that we populate both the fields.
    if (isEmpty(environmentRef)) {
      return environmentRefList.get(0);
    }
    return environmentRef;
  }

  public List<NotificationRuleRefDTO> getNotificationRuleRefs() {
    if (notificationRuleRefs == null) {
      return Collections.emptyList();
    }
    return notificationRuleRefs;
  }

  @Data
  @Builder
  public static class ServiceDependencyDTO {
    @NonNull String monitoredServiceIdentifier;
    ServiceDependencyMetadata dependencyMetadata;
  }

  public Set<ServiceDependencyDTO> getDependencies() {
    if (dependencies == null) {
      return new HashSet<>();
    }
    return dependencies;
  }

  @Data
  @Builder
  public static class Sources {
    @Valid Set<HealthSource> healthSources;
    @Valid Set<ChangeSourceDTO> changeSources;

    public Set<HealthSource> getHealthSources() {
      if (healthSources == null) {
        healthSources = Collections.EMPTY_SET;
      }
      return healthSources;
    }

    public Set<ChangeSourceDTO> getChangeSources() {
      if (changeSources == null) {
        changeSources = Collections.EMPTY_SET;
      }
      return changeSources;
    }
  }
}
