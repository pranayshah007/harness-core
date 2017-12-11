package software.wings.beans.command;

import com.google.inject.Inject;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Transient;
import software.wings.api.ContainerServiceData;
import software.wings.api.DeploymentType;
import software.wings.beans.SettingAttribute;
import software.wings.cloudprovider.ContainerInfo;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.security.encryption.EncryptedDataDetail;

import java.util.List;

/**
 * Created by peeyushaggarwal on 2/3/17.
 */
public class ResizeCommandUnit extends ContainerResizeCommandUnit {
  @Inject @Transient protected transient AwsClusterService awsClusterService;

  public ResizeCommandUnit() {
    super(CommandUnitType.RESIZE);
    setDeploymentType(DeploymentType.ECS.name());
  }

  @Override
  protected List<ContainerInfo> executeInternal(SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, ContainerResizeParams params, ContainerServiceData serviceData,
      ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = (EcsResizeParams) params;
    return awsClusterService.resizeCluster(resizeParams.getRegion(), cloudProviderSetting, encryptedDataDetails,
        resizeParams.getClusterName(), serviceData.getName(), serviceData.getPreviousCount(),
        serviceData.getDesiredCount(), resizeParams.getEcsServiceSteadyStateTimeout(), executionLogCallback);
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("RESIZE")
  public static class Yaml extends ContainerResizeCommandUnit.Yaml {
    public Yaml() {
      super();
      setCommandUnitType(CommandUnitType.RESIZE.name());
    }

    public static final class Builder extends ContainerResizeCommandUnit.Yaml.Builder {
      private Builder() {}

      public static Builder aYaml() {
        return new Builder();
      }

      @Override
      protected Yaml getCommandUnitYaml() {
        return new Yaml();
      }
    }
  }
}
