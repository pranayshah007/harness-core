/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(CDP)
public class TerragruntClientFactory {
  private static final Version MIN_FALLBACK_VERSION = Version.parse("0.0.1");

  @Inject private CliHelper cliHelper;

  public TerragruntClient getClient() {
    return TerragruntClientImpl.builder()
        .terraformVersion(getTerraformVersion())
        .terragruntVersion(getTerragruntVersion())
        .cliHelper(cliHelper)
        .build();
  }

  private Version getTerraformVersion() {
    // implement later
    return MIN_FALLBACK_VERSION;
  }

  private Version getTerragruntVersion() {
    // implement later
    return MIN_FALLBACK_VERSION;
  }
}
