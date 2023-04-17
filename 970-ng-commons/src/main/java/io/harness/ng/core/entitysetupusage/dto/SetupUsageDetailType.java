/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.dto;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;

import lombok.extern.slf4j.Slf4j;

@OwnedBy(DX)
@Slf4j
public enum SetupUsageDetailType {
  SECRET_REFERRED_BY_CONNECTOR,
  CONNECTOR_REFERRED_BY_PIPELINE,
  ENVIRONMENT_REFERRED_BY_PIPELINE,
  INFRASTRUCTURE_REFERRED_BY_PIPELINE,
  ENVIRONMENT_GROUP_REFERRED_BY_PIPELINE,
  SERVICE_REFERRED_BY_PIPELINE,
  SECRET_REFERRED_BY_PIPELINE,
  TEMPLATE_REFERRED_BY_PIPELINE,
  FILES_REFERED_BY_PIPELINE,
  ENTITY_REFERRED_BY_INFRA,

  TEMPLATE_REFERRED_BY_CONNECTOR,
  PIPELINE_REFERED_BY_PIPELINES,
  SECRET_REFERRED_BY_SECRET;

  public static boolean isReferredByPipeline(String setupUsageDetailType) {
    SetupUsageDetailType type = SetupUsageDetailType.valueOf(setupUsageDetailType);
    switch (type) {
      case CONNECTOR_REFERRED_BY_PIPELINE:
      case SECRET_REFERRED_BY_PIPELINE:
      case SERVICE_REFERRED_BY_PIPELINE:
      case ENVIRONMENT_REFERRED_BY_PIPELINE:
      case ENVIRONMENT_GROUP_REFERRED_BY_PIPELINE:
      case TEMPLATE_REFERRED_BY_PIPELINE:
      case FILES_REFERED_BY_PIPELINE:
      case PIPELINE_REFERED_BY_PIPELINES:
      case INFRASTRUCTURE_REFERRED_BY_PIPELINE:
        return true;
      default:
        return false;
    }
  }

  public static SetupUsageDetailType getTypeFromEntityTypeProtoEnumName(String entityTypeProtoEnumName) {
    if (EntityTypeProtoEnum.ENVIRONMENT.name().equals(entityTypeProtoEnumName)) {
      return ENVIRONMENT_REFERRED_BY_PIPELINE;
    } else if (EntityTypeProtoEnum.SERVICE.name().equals(entityTypeProtoEnumName)) {
      return SERVICE_REFERRED_BY_PIPELINE;
    } else if (EntityTypeProtoEnum.CONNECTORS.name().equals(entityTypeProtoEnumName)) {
      return CONNECTOR_REFERRED_BY_PIPELINE;
    } else if (EntityTypeProtoEnum.SECRETS.name().equals(entityTypeProtoEnumName)) {
      return SECRET_REFERRED_BY_PIPELINE;
    } else if (EntityTypeProtoEnum.TEMPLATE.name().equals(entityTypeProtoEnumName)) {
      return TEMPLATE_REFERRED_BY_PIPELINE;
    } else if (EntityTypeProtoEnum.ENVIRONMENT_GROUP.name().equals(entityTypeProtoEnumName)) {
      return ENVIRONMENT_GROUP_REFERRED_BY_PIPELINE;
    } else if (EntityTypeProtoEnum.FILES.name().equals(entityTypeProtoEnumName)) {
      return FILES_REFERED_BY_PIPELINE;
    } else if (EntityTypeProtoEnum.PIPELINES.name().equals(entityTypeProtoEnumName)) {
      return PIPELINE_REFERED_BY_PIPELINES;
    } else if (EntityTypeProtoEnum.INFRASTRUCTURE.name().equals(entityTypeProtoEnumName)) {
      return SetupUsageDetailType.INFRASTRUCTURE_REFERRED_BY_PIPELINE;
    } else {
      return null;
    }
  }
}
