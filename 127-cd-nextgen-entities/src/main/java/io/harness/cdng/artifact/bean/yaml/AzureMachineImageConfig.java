package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AZURE_MACHINE_IMAGE_NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(AZURE_MACHINE_IMAGE_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("AzureMachineImageConfig")
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.AzureMachineImageConfig")
public class AzureMachineImageConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> subscriptionId;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "IMAGE_GALLERY")
  @Wither
  ParameterField<String> imageType;
  @NotNull ImageDefinition imageDefinition;
  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.AZURE_MACHINE_IMAGE;
  }
  @VariableExpression(skipVariableExpression = true) boolean isPrimaryArtifact;
  @EntityIdentifier @VariableExpression(skipVariableExpression = true) String identifier;

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), imageDefinition.name.getValue(),
        imageDefinition.computeGallery.getValue(), imageDefinition.resourceGroup.getValue(), subscriptionId.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public boolean isPrimaryArtifact() {
    return isPrimaryArtifact;
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    AzureMachineImageConfig azureMachineImageConfig = (AzureMachineImageConfig) overrideConfig;
    AzureMachineImageConfig resultantConfig = this;
    if (!ParameterField.isNull(azureMachineImageConfig.connectorRef)) {
      resultantConfig = resultantConfig.withConnectorRef(azureMachineImageConfig.connectorRef);
    }
    if (!ParameterField.isNull(azureMachineImageConfig.getImageType())) {
      resultantConfig = resultantConfig.withImageType(azureMachineImageConfig.getImageType());
    }
    if (!ParameterField.isNull(azureMachineImageConfig.subscriptionId)) {
      resultantConfig = resultantConfig.withSubscriptionId(azureMachineImageConfig.subscriptionId);
    }
    //        if(!ParameterField.isNull(azureMachineImageConfig.imageDefinition.getComputeGallery()) &&
    //        !ParameterField.isNull(azureMachineImageConfig.imageDefinition.getName()) &&
    //        !ParameterField.isNull(azureMachineImageConfig.imageDefinition.getComputeGallery()))
    //        {
    //            resultantConfig = resultantConfig.imageDefinition;
    ////            resultantConfig = resultantConfig(azureMachineImageConfig.imageDefinition);
    //        }
    return resultantConfig;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
