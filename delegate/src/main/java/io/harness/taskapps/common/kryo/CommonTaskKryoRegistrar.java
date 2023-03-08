package io.harness.taskapps.common.kryo;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoRegistrar;

import software.wings.beans.SerializationFormat;

import com.esotericsoftware.kryo.Kryo;

public class CommonTaskKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(final Kryo kryo) {
    // DelegateTaskPackage
    kryo.register(DelegateTaskPackage.class, 7150);
    kryo.register(TaskData.class, 19002);
    kryo.register(TaskDataV2.class, 19005);
    kryo.register(SerializationFormat.class, 55402);
    kryo.register(ExecutionCapability.class, 19343);

    kryo.register(SecretDetail.class, 19001);
    kryo.register(EncryptionConfig.class, 5305);
    kryo.register(EncryptedRecordData.class, 1401);
    kryo.register(EncryptedDataParams.class, 1413);
    kryo.register(EncryptionType.class, 5123);
    kryo.register(AdditionalMetadata.class, 72101);

    // DelegateTaskResponse
    kryo.register(DelegateTaskResponse.class, 5006);
    kryo.register(DelegateTaskResponse.ResponseCode.class, 5520);
  }
}
