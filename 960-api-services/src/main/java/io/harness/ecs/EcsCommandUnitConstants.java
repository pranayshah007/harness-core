package io.harness.ecs;

public enum EcsCommandUnitConstants {
  fetchFiles {
    @Override
    public String toString() {
      return "Fetch Ecs Manifests";
    }
  },
  deploy {
    @Override
    public String toString() {
      return "Deploy";
    }
  }
}
