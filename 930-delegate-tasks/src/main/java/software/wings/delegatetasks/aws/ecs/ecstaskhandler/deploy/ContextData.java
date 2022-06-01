/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.command.EcsResizeParams;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
class ContextData {
  private final AwsConfig awsConfig;
  private final List<EncryptedDataDetail> encryptedDataDetails;
  private final EcsResizeParams resizeParams;
  private final boolean deployingToHundredPercent;
  private final boolean blueGreen;

  public SettingAttribute getSettingAttribute() {
    return SettingAttribute.Builder.aSettingAttribute()
        .withValue(awsConfig)
        .withCategory(SettingCategory.CLOUD_PROVIDER)
        .build();
  }
}
