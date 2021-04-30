package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.beans.LevelNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.S3)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("s3Store")
public class S3StoreConfig implements StoreConfig, Visitable, WithConnectorRef {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> connectorRef;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> bucketName;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> region;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> folderPath;

  @Override
  public String getKind() {
    return ManifestStoreType.S3;
  }

  @Override
  public StoreConfig cloneInternal() {
    return S3StoreConfig.builder()
        .connectorRef(connectorRef)
        .bucketName(bucketName)
        .region(region)
        .folderPath(folderPath)
        .build();
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    S3StoreConfig s3StoreConfig = (S3StoreConfig) overrideConfig;
    S3StoreConfig resultantS3Store = this;
    if (!ParameterField.isNull(s3StoreConfig.getConnectorRef())) {
      resultantS3Store = resultantS3Store.withConnectorRef(s3StoreConfig.getConnectorRef());
    }

    if (!ParameterField.isNull(s3StoreConfig.getBucketName())) {
      resultantS3Store = resultantS3Store.withBucketName(s3StoreConfig.getBucketName());
    }

    if (!ParameterField.isNull(s3StoreConfig.getRegion())) {
      resultantS3Store = resultantS3Store.withRegion(s3StoreConfig.getRegion());
    }

    if (!ParameterField.isNull(s3StoreConfig.getFolderPath())) {
      resultantS3Store = resultantS3Store.withFolderPath(s3StoreConfig.getFolderPath());
    }

    return resultantS3Store;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public LevelNode getLevelNode() {
    return LevelNode.builder().qualifierName("spec").isPartOfFQN(false).build();
  }
}
