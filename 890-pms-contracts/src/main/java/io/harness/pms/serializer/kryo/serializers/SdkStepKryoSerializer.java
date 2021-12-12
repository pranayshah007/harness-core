package io.harness.pms.serializer.kryo.serializers;

import io.harness.pms.contracts.steps.SdkStep;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

public class SdkStepKryoSerializer extends ProtobufKryoSerializer<SdkStep> {
  private static SdkStepKryoSerializer instance;

  private SdkStepKryoSerializer() {}

  public static synchronized SdkStepKryoSerializer getInstance() {
    if (instance == null) {
      instance = new SdkStepKryoSerializer();
    }
    return instance;
  }
}
