package io.harness.connector.entities.embedded.pcfconnector;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@FieldNameConstants(innerTypeName = "PcfManualCredentialKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("io.harness.connector.entities.embedded.pcfconnector.PcfManualCredential")
public class PcfManualCredential implements PcfCredential {
  String endpointUrl;
  String userName;
  String passwordRef;
}
