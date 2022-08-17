package io.harness.cdng.service;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName("undefined")
//@SimpleVisitorHelper(helperClass = KubernetesServiceSpecVisitorHelper.class)
@TypeAlias("DummyServiceSpec")
@RecasterAlias("io.harness.cdng.service.beans.DummyServiceSpec")
@OwnedBy(HarnessTeam.CDP)
public class DummyServiceSpec implements ServiceSpec {
  @Override
  public String getUuid() {
    return null;
  }

  @Override
  public ArtifactListConfig getArtifacts() {
    return null;
  }

  @Override
  public List<ManifestConfigWrapper> getManifests() {
    return null;
  }

  @Override
  public List<NGVariable> getVariables() {
    return null;
  }

  @Override
  public List<ConfigFileWrapper> getConfigFiles() {
    return null;
  }

  @Override
  public String getType() {
    return null;
  }
}
