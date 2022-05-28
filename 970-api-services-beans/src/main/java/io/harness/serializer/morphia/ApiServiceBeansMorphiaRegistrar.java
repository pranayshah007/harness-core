/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.deployment.InstanceDetails;
import io.harness.globalcontex.AuditGlobalContextData;
import io.harness.globalcontex.PurgeGlobalContextData;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.shell.ShellExecutionData;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.command.CodeDeployCommandExecutionData;
import software.wings.beans.command.ResizeCommandUnitExecutionData;

import java.util.Set;

public class ApiServiceBeansMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(EncryptionConfig.class);
    set.add(EncryptedRecord.class);
    set.add(EncryptableSetting.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("globalcontex.AuditGlobalContextData", AuditGlobalContextData.class);
    h.put("globalcontex.PurgeGlobalContextData", PurgeGlobalContextData.class);
    h.put("deployment.InstanceDetails", InstanceDetails.class);
    h.put("shell", ShellExecutionData.class);
    w.put("beans.command.CodeDeployCommandExecutionData", CodeDeployCommandExecutionData.class);
    w.put("beans.command.ResizeCommandUnitExecutionData", ResizeCommandUnitExecutionData.class);
    w.put("beans.command.ShellExecutionData", ShellExecutionData.class);
  }
}
