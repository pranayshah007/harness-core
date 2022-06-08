package io.harness.ci.integrationstage;

import static io.harness.pms.yaml.ParameterField.createValueField;

import static java.util.Arrays.asList;

import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml.EmptyDirYamlSpec;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml.HostPathYamlSpec;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml.PersistentVolumeClaimYamlSpec;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume;
import io.harness.delegate.beans.ci.pod.HostPathVolume;
import io.harness.delegate.beans.ci.pod.PVCVolume;
import io.harness.delegate.beans.ci.pod.PodVolume;

import java.util.List;

public class K8InitializeTaskUtilsHelper {
  public static final String K8_CONNECTOR = "testKubernetesCluster";
  public static final String K8_NAMESPACE = "testNamespace";

  public static final String EMPTY_DIR_SIZE = "1Gi";
  public static final String EMPTY_DIR_MOUNT_PATH = "/empty";
  public static final String HOST_DIR_MOUNT_PATH = "/host";
  public static final String PVC_DIR_MOUNT_PATH = "/pvc";
  public static final String PVC_CLAIM_NAME = "pvc";

  public static K8sDirectInfraYaml getDirectK8Infrastructure() {
    List<CIVolume> volumes = asList(getEmptyDirVolYaml(), getHostPathVolYaml(), getPVCYaml());
    return K8sDirectInfraYaml.builder()
        .type(Infrastructure.Type.KUBERNETES_DIRECT)
        .spec(K8sDirectInfraYamlSpec.builder()
                  .connectorRef(createValueField(K8_CONNECTOR))
                  .namespace(createValueField(K8_NAMESPACE))
                  .volumes(createValueField(volumes))
                  .build())
        .build();
  }

  private static EmptyDirYaml getEmptyDirVolYaml() {
    return EmptyDirYaml.builder()
        .mountPath(createValueField(EMPTY_DIR_MOUNT_PATH))
        .spec(EmptyDirYamlSpec.builder().size(createValueField(EMPTY_DIR_SIZE)).medium(createValueField(null)).build())
        .build();
  }

  private static HostPathYaml getHostPathVolYaml() {
    return HostPathYaml.builder()
        .mountPath(createValueField(HOST_DIR_MOUNT_PATH))
        .spec(
            HostPathYamlSpec.builder().path(createValueField(HOST_DIR_MOUNT_PATH)).type(createValueField(null)).build())
        .build();
  }

  private static PersistentVolumeClaimYaml getPVCYaml() {
    return PersistentVolumeClaimYaml.builder()
        .mountPath(createValueField(PVC_DIR_MOUNT_PATH))
        .spec(PersistentVolumeClaimYamlSpec.builder()
                  .claimName(createValueField(PVC_CLAIM_NAME))
                  .readOnly(createValueField(null))
                  .build())
        .build();
  }

  public static List<PodVolume> getConvertedVolumes() {
    EmptyDirVolume emptyDirVolume =
        EmptyDirVolume.builder().name("volume-0").mountPath(EMPTY_DIR_MOUNT_PATH).sizeMib(1024).build();
    HostPathVolume hostPathVolume =
        HostPathVolume.builder().name("volume-1").mountPath(HOST_DIR_MOUNT_PATH).path(HOST_DIR_MOUNT_PATH).build();
    PVCVolume pvcVolume =
        PVCVolume.builder().name("volume-2").mountPath(PVC_DIR_MOUNT_PATH).claimName(PVC_CLAIM_NAME).build();

    return asList(emptyDirVolume, hostPathVolume, pvcVolume);
  }
}
