package io.harness.pms.serializer.kryo.serializers;

import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

public class ConsumerConfigKryoSerializer extends ProtobufKryoSerializer<ConsumerConfig> {
  private static ConsumerConfigKryoSerializer instance;

  private ConsumerConfigKryoSerializer() {}

  public static synchronized ConsumerConfigKryoSerializer getInstance() {
    if (instance == null) {
      instance = new ConsumerConfigKryoSerializer();
    }
    return instance;
  }
}
