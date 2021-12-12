package io.harness.beans.outcomes;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("vmDetailsOutcome")
@JsonTypeName("vmDetailsOutcome")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.outcomes.VmDetailsOutcome")
public class VmDetailsOutcome implements Outcome {
  String ipAddress;
  public static final String VM_DETAILS_OUTCOME = "vmDetailsOutcome";
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
}
