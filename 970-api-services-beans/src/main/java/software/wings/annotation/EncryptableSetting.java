/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.annotation;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.encryption.Encryptable;

import software.wings.settings.SettingVariableTypes;

import com.github.reinert.jjschema.SchemaIgnore;

@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._957_CG_BEANS)
public interface EncryptableSetting extends Encryptable {
  @SchemaIgnore SettingVariableTypes getSettingType();
}
