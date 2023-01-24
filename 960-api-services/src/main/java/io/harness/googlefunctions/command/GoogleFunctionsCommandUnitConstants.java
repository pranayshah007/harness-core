package io.harness.googlefunctions.command;

public enum GoogleFunctionsCommandUnitConstants {
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
  trafficShift {
    @Override
    public String toString() {
      return "Traffic Shift";
    }
  }
}
