/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.beans.entities;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
@TypeAlias("ciExecutionImages")
@RecasterAlias("io.harness.ci.beans.entities.CIExecutionImages")
public class CIExecutionImages {
  @NotBlank String addonTag;
  @NotBlank String liteEngineTag;
  @NotBlank String gitCloneTag;
  @NotBlank String buildAndPushDockerRegistryTag;
  @NotBlank String buildAndPushECRTag;
  @NotBlank String buildAndPushACRTag;
  @NotBlank String buildAndPushGCRTag;
  @NotBlank String gcsUploadTag;
  @NotBlank String s3UploadTag;
  @NotBlank String artifactoryUploadTag;
  @NotBlank String cacheGCSTag;
  @NotBlank String cacheS3Tag;
  @NotBlank String securityTag;
  @NotBlank String sscaOrchestrationTag;
  @NotBlank String sscaEnforcementTag;
}
