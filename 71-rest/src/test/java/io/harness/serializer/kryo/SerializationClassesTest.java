package io.harness.serializer.kryo;

import static io.harness.serializer.kryo.SerializationClasses.serializationClasses;

import io.harness.serializer.KryoUtils;
import org.junit.Test;

import java.util.Map;

public class SerializationClassesTest {
  @Test
  public void kryoInit() {
    KryoUtils.clone(1);
  }

  @Test
  public void serializationClassesShouldExist() throws ClassNotFoundException {
    Map<String, Integer> classIds = serializationClasses();
    for (String s : classIds.keySet()) {
      Class.forName(s);
    }
  }
}
