package io.harness.pms.serializer.kryo.serializers;

import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

public class AdviserObtainmentKryoSerializer extends ProtobufKryoSerializer<AdviserObtainment> {
  private static AdviserObtainmentKryoSerializer instance;

  private AdviserObtainmentKryoSerializer() {}

  public static synchronized AdviserObtainmentKryoSerializer getInstance() {
    if (instance == null) {
      instance = new AdviserObtainmentKryoSerializer();
    }
    return instance;
  }
}
