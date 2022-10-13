package software.wings.timescale.migrations;

public interface TimeScaleEntityMigrationInterface {
  boolean runTimeScaleMigration(String accountId);
  String getTimescaleDBClass();
  void deleteFromTimescale(String id);
}
