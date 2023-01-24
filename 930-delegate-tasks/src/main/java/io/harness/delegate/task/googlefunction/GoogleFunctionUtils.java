package io.harness.delegate.task.googlefunction;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.serializer.YamlUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class GoogleFunctionUtils {
  private static final YamlUtils yamlUtils = new YamlUtils();

  public <T> T parseYamlToObjectSchema(String yaml, Class<T> tClass, String schema) {
    T object;
    try {
      object = yamlUtils.read(yaml, tClass);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Please check yaml configured matches schema %s", schema),
          format("Error while parsing yaml %s. Its expected to be matching %s schema. Please check Harness "
                  + "documentation https://docs.harness.io for more details",
              yaml, schema),
          e);
    }
    return object;
  }
}
