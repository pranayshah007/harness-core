/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params;

import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@SuperBuilder
public class MonitoredServiceParams extends ProjectParams {
  @QueryParam("serviceIdentifier") @Deprecated String serviceIdentifier;
  @QueryParam("environmentIdentifier") @Deprecated String environmentIdentifier;
  @NotNull String monitoredServiceIdentifier;
  // Only for migration code.
  @Deprecated
  public static MonitoredServiceParamsBuilder builderWithServiceEnvParams(
      ServiceEnvironmentParams serviceEnvironmentParams) {
    return MonitoredServiceParams.builder()
        .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
        .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
        .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
        .serviceIdentifier(serviceEnvironmentParams.getServiceIdentifier())
        .environmentIdentifier(serviceEnvironmentParams.getEnvironmentIdentifier());
  }

  public ServiceEnvironmentParams getServiceEnvironmentParams() {
    return ServiceEnvironmentParams.builderWithProjectParams(this)
        .serviceIdentifier(getServiceIdentifier())
        .environmentIdentifier(getEnvironmentIdentifier())
        .build();
  }

  public static MonitoredServiceParamsBuilder builderWithProjectParams(ProjectParams projectParams) {
    return MonitoredServiceParams.builder()
        .orgIdentifier(projectParams.getOrgIdentifier())
        .accountIdentifier(projectParams.getAccountIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier());
  }
}
