package io.harness.cdng.ecs;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;

public class EcsStepUtils extends CDStepHelper {
  @Inject private EcsEntityHelper ecsEntityHelper;

  public GitStoreDelegateConfig getGitStoreDelegateConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Ecs manifest with Id [%s]", manifestOutcome.getIdentifier());
    ConnectorInfoDTO connectorDTO = getConnectorDTO(connectorId, ambiance);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    return getGitStoreDelegateConfig(
        gitStoreConfig, connectorDTO, manifestOutcome, gitStoreConfig.getPaths().getValue(), ambiance);
  }

  private ConnectorInfoDTO getConnectorDTO(String connectorId, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return ecsEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
  }
}
