/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import lombok.Data;

@Data
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
public class AksGetCredentialsResponse {
  private String id;
  private String location;
  private String name;
  private String type;
  private AksGetCredentialProperties properties;

  @Data
  public class AksGetCredentialProperties {
    private String kubeConfig;
  }
}
