package io.harness.pms.serializer.kryo.serializers;

import io.harness.pms.contracts.plan.SdkModuleInfo;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

public class SdkModuleInfoKryoSerializer extends ProtobufKryoSerializer<SdkModuleInfo> {
  private static SdkModuleInfoKryoSerializer instance;

  private SdkModuleInfoKryoSerializer() {}

  public static synchronized SdkModuleInfoKryoSerializer getInstance() {
    if (instance == null) {
      instance = new SdkModuleInfoKryoSerializer();
    }
    return instance;
  }
}
