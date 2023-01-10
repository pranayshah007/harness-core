package io.harness.cdng.infra.beans;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@Data
@Builder
@TypeAlias("googleFunctionsInfraMapping")
@JsonTypeName("googleFunctionsInfraMapping")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.infra.beans.GoogleFunctionsInfraMapping")
public class GoogleFunctionsInfraMapping implements InfraMapping {
    @Id private String uuid;
    private String accountId;
    private String gcpConnector;
    private String project;
    private String region;
}
