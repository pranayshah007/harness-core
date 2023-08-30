/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("CrossAccountAccess")
@Schema(name = "CrossAccountAccess", description = "This contains AWS connector cross account access details")
@RecasterAlias("io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO")
public class CrossAccountAccessDTO {
  @NotNull private String crossAccountRoleArn;
  private String externalId;

  @Builder
  public CrossAccountAccessDTO(String crossAccountRoleArn, String externalId) {
    this.crossAccountRoleArn = crossAccountRoleArn;
    this.externalId = externalId;
  }
}
