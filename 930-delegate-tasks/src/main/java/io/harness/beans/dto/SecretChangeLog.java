package io.harness.beans.dto;

import io.harness.beans.EmbeddedUser;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class SecretChangeLog {
  private String accountId;
  private String encryptedDataId;
  private EmbeddedUser user;
  private String description;
  private EmbeddedUser createdBy;
  private long createdAt;
  private EmbeddedUser lastUpdatedBy;
  private long lastUpdatedAt;
  private boolean external;
}
