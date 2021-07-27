package io.harness.nexus.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

@lombok.Data
@Builder
@OwnedBy(HarnessTeam.CDC)
public class Nexus3Request {
  private String action;
  private String method;
  @JsonProperty("data") private List<Nexus3RequestData> data;
  private String type;
  private int tid;
}
