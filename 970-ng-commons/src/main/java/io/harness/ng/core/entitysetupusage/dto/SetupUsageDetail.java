/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.dto;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(DX)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true, property = "type")
@JsonSubTypes({
  @JsonSubTypes.
  Type(value = SecretReferredByConnectorSetupUsageDetail.class, name = "SecretReferredByConnectorSetupUsageDetail")
  ,
      @JsonSubTypes.Type(
          value = EntityReferredByPipelineSetupUsageDetail.class, name = "EntityReferredByPipelineSetupUsageDetail"),
      @JsonSubTypes.Type(
          value = EntityReferredByInfraSetupUsageDetail.class, name = "EntityReferredByInfraSetupUsageDetail")
})
public interface SetupUsageDetail {}
