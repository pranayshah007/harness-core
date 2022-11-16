package io.harness.delegate.beans.instancesync.info;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("TasServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class TasServerInstanceInfo extends ServerInstanceInfo {
  private String id;
  private String organization;
  private String space;
  private String tasApplicationName;
  private String tasApplicationGuid;
  private String instanceIndex;
}
