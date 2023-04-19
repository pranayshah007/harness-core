/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("serviceStepOutcome")
@JsonTypeName("serviceStepOutcome")
@RecasterAlias("io.harness.cdng.service.steps.ServiceStepOutcome")
public class ServiceStepOutcome implements Outcome, ExecutionSweepingOutput {
  String identifier;
  String name;
  String description;
  String type;
  Map<String, String> tags;
  boolean gitOpsEnabled;

  public String getServiceDefinitionType() {
    return type;
  }

  public static ServiceStepOutcome fromServiceEntity(String type, ServiceEntity serviceEntity) {
    if (serviceEntity == null) {
      return null;
    }

    return ServiceStepOutcome.builder()
        .identifier(serviceEntity.getIdentifier())
        .name(serviceEntity.getName())
        .description(serviceEntity.getDescription())
        .type(type)
        .tags(serviceEntity.getTags() == null
                ? Collections.emptyMap()
                : serviceEntity.getTags().stream().collect(Collectors.toMap(NGTag::getKey, NGTag::getValue)))
        .build();
  }

  public static ServiceStepOutcome fromServiceStepV2(String scopedIdentifierRef, String name, String type,
      String description, Map<String, String> tags, Boolean gitOpsEnabled) {
    return ServiceStepOutcome.builder()
        .identifier(scopedIdentifierRef)
        .name(name)
        .description(description)
        .tags(tags)
        .type(type)
        .gitOpsEnabled(gitOpsEnabled == TRUE)
        .build();
  }

  public static ServiceStepOutcome fromServiceStepV2(
      ServiceEntity service, NGServiceV2InfoConfig ngServiceV2InfoConfig) {
    return ServiceStepOutcome.builder()
        .identifier(IdentifierRefHelper.getRefFromIdentifierOrRef(service.getAccountId(), service.getOrgIdentifier(),
            service.getProjectIdentifier(), service.getIdentifier()))
        .name(ngServiceV2InfoConfig.getName())
        .description(ngServiceV2InfoConfig.getDescription())
        .type(ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName())
        .tags(ngServiceV2InfoConfig.getTags())
        .gitOpsEnabled(
            ngServiceV2InfoConfig.getGitOpsEnabled() == null ? FALSE : ngServiceV2InfoConfig.getGitOpsEnabled())
        .build();
  }
}
