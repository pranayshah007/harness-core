package io.harness.ng.core.dto;

import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@ApiModel(value = "Project")
@Schema(name = "Project", description = "This is the Project Entity details defined in Harness")
public class ProjectDTO {
  @EntityIdentifier(allowBlank = true) @Schema(description = ORG_PARAM_MESSAGE) String orgIdentifier;
  @ApiModelProperty(required = true)
  @Schema(description = PROJECT_PARAM_MESSAGE)
  @EntityIdentifier(allowBlank = false)
  String identifier;
  @ApiModelProperty(required = true) @Schema(description = "Project Name for the entity") @NGEntityName String name;
  @Schema(description = "Color") String color;
  @Size(max = 1024) @Schema(description = "List of modules") List<ModuleType> modules;
  @Size(max = 1024) @Schema(description = "Description") String description;
  @Size(max = 128) @Schema(description = "Tags") Map<String, String> tags;
  @JsonIgnore Long version;

  @Builder
  public ProjectDTO(String orgIdentifier, String identifier, String name, String color, List<ModuleType> modules,
      String description, Map<String, String> tags) {
    this.orgIdentifier = orgIdentifier;
    this.identifier = identifier;
    this.name = name;
    this.color = color;
    this.modules = modules;
    this.description = description;
    this.tags = tags;
  }
}
