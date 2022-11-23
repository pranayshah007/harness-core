package io.harness.serializer.kryo;

import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class ContainerKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(K8sDirectInfraYamlSpec.class, 100041);

    kryo.register(CIVolume.class, 390005);
    kryo.register(EmptyDirYaml.class, 390006);
    kryo.register(HostPathYaml.class, 390007);
    kryo.register(PersistentVolumeClaimYaml.class, 390008);
  }
}
