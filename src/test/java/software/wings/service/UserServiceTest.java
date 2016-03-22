package software.wings.service;

import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import software.wings.audit.AuditHeader;
import software.wings.beans.AuthToken;
import software.wings.beans.Permission;
import software.wings.beans.Role;
import software.wings.beans.User;
import software.wings.dl.MongoConnectionFactory;

import java.util.Collections;

import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

/**
 * Created by anubhaw on 3/9/16.
 */

public class UserServiceTest {
  private Datastore getDataStore() {
    MongoConnectionFactory factory = new MongoConnectionFactory();
    factory.setDb("wings");
    factory.setHost("localhost");
    return factory.getDatastore();
  }

  Datastore datastore = getDataStore();

  UserService userService = new UserService(getDataStore());

  @Test
  public void testRegister() throws Exception {
    User user = new User();
    user.setEmail("user1@wings.software");
    user.setName("John Doe");
    user.setPasswordHash("password");
    userService.register(user);
  }

  @Test
  public void testAssignRoleToUser() {
    Role role = new Role();
    role.setUuid("35D7D2C04A164655AB732B963A5DD308");
    Query<User> updateQuery = datastore.createQuery(User.class).field(ID_KEY).equal("D3BB4DEA57D043BCA73597CCDE01E637");
    UpdateOperations<User> updateOperations = datastore.createUpdateOperations(User.class).add("roles", role);
    datastore.update(updateQuery, updateOperations);
  }

  @Test
  public void testMatchPassword() throws Exception {
    long startTime = System.currentTimeMillis();
    System.out.println(
        userService.matchPassword("password", "$2a$10$ygoANZ1GfZf09oUDCcDLuO1cWt7x2XDl/Dq3J.sYgkC51KDEMK64C"));
    System.out.println(System.currentTimeMillis() - startTime);
  }

  @Test
  public void testCreateRole() {
    Permission permission = new Permission("ALL", "ALL", "ALL", "ALL");
    Role role = new Role("ADMIN", "Administrator role. It can access resource and perform any action",
        Collections.singletonList(permission));
    datastore.save(role);
  }

  @Test
  public void testHelper() {
    User user = datastore.get(User.class, "D3BB4DEA57D043BCA73597CCDE01E637");
    System.out.println(user);
  }
}