package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("InputSetSummaryResponse")
@Schema(name = "InputSetSummaryResponse", description = "This is the view of the Input Set Summary.")
public class InputSetSummaryResponseDTOPMS {
  String identifier;
  String name;
  String pipelineIdentifier;
  String description;
  InputSetEntityType inputSetType;
  Map<String, String> tags;
  @JsonIgnore Long version;
  EntityGitDetails gitDetails;
  Long createdAt;
  Long lastUpdatedAt;
  Boolean isOutdated;
  InputSetErrorWrapperDTOPMS inputSetErrorDetails;
  Map<String, String> overlaySetErrorDetails;
  EntityValidityDetails entityValidityDetails;
  Set<String> modules;
}
