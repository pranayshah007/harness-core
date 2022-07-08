package io.harness.ng.chaos;

public interface ChaosService {
  void applyK8sManifest(ChaosK8sRequest chaosK8sRequest);
}
