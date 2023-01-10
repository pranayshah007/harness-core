/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.beans.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.pipeline.v1.model.InputsResponseBodyRepository;
import io.harness.spec.server.pipeline.v1.model.InputsResponseBodyRepositoryReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@OwnedBy(PIPELINE)
public class RepositoryInput extends InputsResponseBodyRepository {
  private ReferenceInput reference;

  @Data
  @Builder
  @EqualsAndHashCode(callSuper = true)
  public static class ReferenceInput extends InputsResponseBodyRepositoryReference {
    private InputEntity type;
    private InputEntity value;
  }
}
