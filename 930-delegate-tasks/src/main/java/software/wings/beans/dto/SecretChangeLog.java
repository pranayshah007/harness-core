package software.wings.beans.dto;

import io.harness.beans.EmbeddedUser;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
public class SecretChangeLog {
  private String uuid;
  private String accountId;
  @NotEmpty private String encryptedDataId;
  @NotNull private EmbeddedUser user;
  @NotEmpty private String description;
  private EmbeddedUser createdBy;
  private long createdAt;
  private EmbeddedUser lastUpdatedBy;
  @NotNull private long lastUpdatedAt;
  // Secret change log could be retrieved from external system such as Vault (secret versions metadata)
  // This flag is used to denote if this log entry is originated from external system.
  private boolean external;
}
