package com.groupcc2.recruitment.profile;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
/** Real restricted-role policies. Bootstrap connection is used only for fixture provisioning. */
@Testcontainers
class ProfileRlsIT {
 @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:17");
 @Test void ownerIsolationEmployerVisibilityAndTransactionLocalContext() throws Exception {
 Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).load().migrate();
 UUID a=UUID.randomUUID(),b=UUID.randomUUID(),employer=UUID.randomUUID(),otherEmployer=UUID.randomUUID(),job=UUID.randomUUID();
 try(var root=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword());var s=root.createStatement()) {
 s.execute("CREATE ROLE profile_runtime LOGIN PASSWORD 'test-only' NOSUPERUSER NOBYPASSRLS");
 s.execute("GRANT USAGE ON SCHEMA recruitment TO profile_runtime");s.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA recruitment TO profile_runtime");
 for(UUID id:new UUID[]{a,b,employer,otherEmployer})s.execute("INSERT INTO recruitment.users(id,email,password_hash,role) VALUES ('"+id+"','"+id+"@test','hash','"+(id.equals(a)||id.equals(b)?"JOB_SEEKER":"EMPLOYER")+"')");
 for(UUID id:new UUID[]{a,b}) {
 s.execute("INSERT INTO recruitment.profiles(user_id,display_name) VALUES ('"+id+"','name')");
 s.execute("INSERT INTO recruitment.documents(profile_user_id,storage_key,original_filename,content_type,byte_size) VALUES ('"+id+"','"+id+"','cv.pdf','application/pdf',20)");
 }
 s.execute("INSERT INTO recruitment.jobs(id,employer_id,title,description,due_date) VALUES ('"+job+"','"+employer+"','Job','Description',CURRENT_DATE+7)");
 }
 try(var c=DriverManager.getConnection(db.getJdbcUrl(),"profile_runtime","test-only");var s=c.createStatement()) {
 try(var r=s.executeQuery("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user")){r.next();assertThat(r.getBoolean(1)).isFalse();}
 c.setAutoCommit(false);context(s,a,"JOB_SEEKER");assertThat(count(s,"SELECT count(*) FROM recruitment.profiles")).isEqualTo(1);
 assertThat(s.executeUpdate("UPDATE recruitment.profiles SET display_name='stolen' WHERE user_id='"+b+"'")).isZero();
 c.commit();assertThat(count(s,"SELECT count(*) FROM recruitment.profiles")).isZero();c.commit();
 context(s,employer,"EMPLOYER");assertThat(count(s,"SELECT count(*) FROM recruitment.profiles")).isZero();c.commit();
 context(s,a,"JOB_SEEKER");s.execute("INSERT INTO recruitment.applications(job_id,job_seeker_id) VALUES ('"+job+"','"+a+"')");c.commit();
 context(s,employer,"EMPLOYER");assertThat(count(s,"SELECT count(*) FROM recruitment.profiles")).isEqualTo(1);
 assertThat(s.executeUpdate("DELETE FROM recruitment.profiles WHERE user_id='"+a+"'")).isZero();
 assertThat(count(s,"SELECT count(*) FROM recruitment.documents")).isEqualTo(1);
 assertThat(s.executeUpdate("DELETE FROM recruitment.documents WHERE profile_user_id='"+a+"'")).isZero();c.commit();
 context(s,otherEmployer,"EMPLOYER");assertThat(count(s,"SELECT count(*) FROM recruitment.profiles")).isZero();c.commit();
 }
 }
 void context(Statement s,UUID id,String role)throws SQLException {s.execute("SELECT set_config('app.user_id','"+id+"',true),set_config('app.role','"+role+"',true)");}
 int count(Statement s,String sql)throws SQLException {try(var r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
}
