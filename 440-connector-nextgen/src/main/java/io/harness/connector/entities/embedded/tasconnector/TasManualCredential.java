package io.harness.connector.entities.embedded.tasconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@FieldNameConstants(innerTypeName = "TasManualCredentialKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("io.harness.connector.entities.embedded.tasconnector.TasManualCredential")
public class TasManualCredential implements TasCredential {
  String userName;
  String endpointUrl;
  String userNameRef;
  String passwordRef;
}
