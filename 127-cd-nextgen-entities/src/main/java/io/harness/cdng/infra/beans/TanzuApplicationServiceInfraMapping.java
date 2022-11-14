package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("TanzuApplicationServiceInfraMapping")
@JsonTypeName("TanzuApplicationServiceInfraMapping")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.beans.TanzuApplicationServiceInfraMapping")
public class TanzuApplicationServiceInfraMapping implements InfraMapping {
  @Id private String uuid;
  private String accountId;
  private String connectorRef;
  private String organization;
  private String space;
}
