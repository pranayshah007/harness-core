/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.platform;

import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoRegistrar;
import io.harness.shell.CommandExecutionData;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutionData;

import software.wings.beans.bash.ShellScriptParameters;

import com.esotericsoftware.kryo.Kryo;

public class DelegatePlatformRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // FixMe: These registrations are generally needed but are temporarely comming from other registrars so living them
    // commented out for now
    //
    // DelegateTaskPackage
    //    kryo.register(DelegateTaskPackage.class, 7150);
    //    kryo.register(TaskData.class, 19002);
    kryo.register(ShellScriptParameters.class, 5186);
    //    kryo.register(SerializationFormat.class, 55402);
    //    kryo.register(ExecutionCapability.class, 19343);
    //    kryo.register(SecretDetail.class, 19001);

    kryo.register(EncryptionConfig.class, 5305);
    kryo.register(EncryptedRecordData.class, 1401);
    kryo.register(EncryptedDataParams.class, 1413);
    kryo.register(EncryptionType.class, 5123);
    kryo.register(AdditionalMetadata.class, 72101);
    kryo.register(ScriptType.class, 5253);

    // DelegateTaskResponse
    //    kryo.register(DelegateTaskResponse.class, 5006);
    //    kryo.register(DelegateTaskResponse.ResponseCode.class, 5520);
    //    kryo.register(CommandExecutionResult.class, 5036);
    //    kryo.register(DelegateTaskNotifyResponseData.class, 5373);
    //    kryo.register(DelegateMetaInfo.class, 5372);
    kryo.register(CommandExecutionStatus.class, 5037);
    kryo.register(CommandExecutionData.class, 5035); // ??
    kryo.register(ShellExecutionData.class, 5528);
    //    kryo.register(TaskType.class, 5005);
  }
}
