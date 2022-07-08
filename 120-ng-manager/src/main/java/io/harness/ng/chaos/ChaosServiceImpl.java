package io.harness.ng.chaos;

import com.google.inject.Singleton;

@Singleton
public class ChaosServiceImpl implements ChaosService {
  @Override
  public void applyK8sManifest(ChaosK8sRequest chaosK8sRequest) {
    // TODO(abhinav): apply logic
  }
}
