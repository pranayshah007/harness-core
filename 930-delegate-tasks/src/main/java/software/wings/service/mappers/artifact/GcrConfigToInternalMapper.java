/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.mappers.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.gcr.beans.GcrInternalConfig;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class GcrConfigToInternalMapper {
  public GcrInternalConfig toGcpInternalConfig(String gcrHostName, String basicAuthHeader) {
    return GcrInternalConfig.builder().basicAuthHeader(basicAuthHeader).registryHostname(gcrHostName).build();
  }
}
