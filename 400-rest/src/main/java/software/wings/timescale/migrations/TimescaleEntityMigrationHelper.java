package software.wings.timescale.migrations;

import io.fabric8.utils.Lists;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class TimescaleEntityMigrationHelper {
  public static void insertArrayData(
      int index, Connection dbConnection, PreparedStatement preparedStatement, List<String> data) throws SQLException {
    if (!Lists.isNullOrEmpty(data)) {
      Array array = dbConnection.createArrayOf("text", data.toArray());
      preparedStatement.setArray(index, array);
    } else {
      preparedStatement.setArray(index, null);
    }
  }
}
