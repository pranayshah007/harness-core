package io.harness.ng.core.template;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("TemplateScope")
@Schema(name = "TemplateScope", description = "This contains scope of template being created")
@OwnedBy(PL)
public class ListingScope {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
}
