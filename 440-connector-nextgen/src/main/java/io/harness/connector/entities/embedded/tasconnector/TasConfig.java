package io.harness.connector.entities.embedded.tasconnector;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.ng.DbAliases;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@FieldNameConstants(innerTypeName = "TasConnectorKeys")
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.connector.entities.embedded.tasconnector.TasConfig")
public class TasConfig extends Connector {
  TasCredentialType credentialType;
  TasCredential credential;
}
