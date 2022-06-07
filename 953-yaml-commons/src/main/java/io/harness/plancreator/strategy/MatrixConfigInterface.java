package io.harness.plancreator.strategy;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(CDC)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY,
    defaultImpl = MatrixConfig.class)
@JsonSubTypes({ @JsonSubTypes.Type(value = MatrixConfig.class, name = "matrix") })
@ApiModel("MatrixConfigInterface")
@Schema(name = "MatrixConfigInterface", description = "Interface for Matrix Config")
public interface MatrixConfigInterface {}
