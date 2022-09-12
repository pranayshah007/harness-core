package io.harness.ecs;

public enum EcsCommandUnitConstants {
  fetchManifests {
    @Override
    public String toString() {
      return "Fetch Manifests";
    }
  },
  prepareRollbackData {
    @Override
    public String toString() {
      return "Prepare Rollback Data";
    }
  },
  deploy {
    @Override
    public String toString() {
      return "Deploy";
    }
  },
  rollback {
    @Override
    public String toString() {
      return "Rollback";
    }
  },
  deleteService {
    @Override
    public String toString() {
      return "Delete Service";
    }
  }
}
