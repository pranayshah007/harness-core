package software.wings.beans.dto;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class SecretManagerRuntimeParameters {
  private String uuid;
  private String secretManagerId;
  private String executionId;
  private String runtimeParameters;
  private String accountId;
}
