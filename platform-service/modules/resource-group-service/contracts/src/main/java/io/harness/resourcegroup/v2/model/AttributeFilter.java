package io.harness.resourcegroup.v2.model;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PL)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@FieldNameConstants(innerTypeName = "AttributeFilterKeys")
@ApiModel(value = "AttributeFilter")
@Schema(name = "AttributeFilter", description = "Used to filter resources on their attributes")
public class AttributeFilter {
  String attributeName;
  List<String> attributeValues;
}
