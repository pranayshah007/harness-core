package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
public class DecryptedSecretValue {
  String identifier;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String decryptedValue;
}
