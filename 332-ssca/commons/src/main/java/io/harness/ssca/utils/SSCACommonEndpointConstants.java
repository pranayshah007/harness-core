/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.SSCA)
public class SSCACommonEndpointConstants {
  public static final String SSCA_SERVICE_ARTIFACT_ENDPOINT = "api/v1/artifacts/";
  public static final String SSCA_SERVICE_TOKEN_ENDPOINT = "api/v1/token";
  public static final String SSCA_SERVICE_ENFORCEMENT_ENDPOINT = "api/v1/sbom/enforcement/";
}
