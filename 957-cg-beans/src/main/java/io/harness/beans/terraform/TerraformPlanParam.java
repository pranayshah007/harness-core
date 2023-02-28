/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SweepingOutput;
import io.harness.security.encryption.EncryptedRecordData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName("terraformPlanParam")
@OwnedBy(CDP)
public class TerraformPlanParam implements SweepingOutput {
  private EncryptedRecordData encryptedRecordData;
  private String tfplan;
  private String tfplanHumanReadable;
  private String tfplanHumanReadableFileId;
  private String tfPlanJsonFileId;
  private Integer add;
  private Integer change;
  private Integer destroy;
  private String policyChecksFileId;

  @Override
  public String getType() {
    return "terraformPlanParam";
  }
}
