package io.harness.tasks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public interface SerializableResponseData extends ResponseData {
  byte[] serialize();
}
