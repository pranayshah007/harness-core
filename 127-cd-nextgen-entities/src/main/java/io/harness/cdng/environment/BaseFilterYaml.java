package io.harness.cdng.environment;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.filters.FilterSpec;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.EnvironmentFilterVisitorHelper;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.EnumSet;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@SimpleVisitorHelper(helperClass = EnvironmentFilterVisitorHelper.class)
@TypeAlias("filterYaml")
@RecasterAlias("io.harness.cdng.environment.BaseFilterYaml")
@OwnedBy(HarnessTeam.CDC)
public abstract class BaseFilterYaml<T extends Enum<T>> implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("type") FilterType type;

  @NotNull private EnumSet<T> entities;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FilterSpec spec;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
}