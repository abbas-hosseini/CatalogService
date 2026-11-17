package com.example.webapp.security;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:role-access-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(SecurityTestConfiguration.class)
class RoleAccessTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    SecurityTestClient admin;
    @BeforeEach void prepare() throws Exception { admin = SecurityTestClient.shared(port); }
    long role(String... permissions) throws Exception {
        var response=admin.write("POST","/api/admin/roles",Map.of("name","role-"+UUID.randomUUID(),"permissions",List.of(permissions)),true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return admin.json.readTree(response.body()).get("id").asLong();
    }
    record Account(long id, String username, SecurityTestClient client) {}
    Account user(List<Long> roleIds) throws Exception {
        String username="u"+UUID.randomUUID().toString().replace("-","");
        var response=admin.write("POST","/api/admin/users",Map.of("username",username,"displayName","آزمایش","password",SecurityTestClient.PASSWORD,
            "confirmation",SecurityTestClient.PASSWORD,"enabled",true,"roleIds",roleIds),true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var client=new SecurityTestClient(port);assertThat(client.login(username,SecurityTestClient.PASSWORD).statusCode()).isEqualTo(204);
        return new Account(admin.json.readTree(response.body()).get("id").asLong(),username,client);
    }
    Map<String,Object> updateUser(boolean enabled, List<Long> roles){return Map.of("displayName","آزمایش","email","","enabled",enabled,"roleIds",roles);}
    @Test void employeeSearchHasItsOwnPermissionIncludesEmployeeViewAndRevocationIsImmediate() throws Exception {
        var viewer=user(List.of(role("employees.view")));
        assertThat(viewer.client.get("/api/employees/search").statusCode()).isEqualTo(403);
        long searchRole=role("employee-search.view");var searcher=user(List.of(searchRole));
        assertThat(searcher.client.get("/api/employees/search").statusCode()).isEqualTo(200);
        assertThat(searcher.client.get("/api/employees").statusCode()).isEqualTo(200);
        assertThat(searcher.client.get("/api/access/me").body()).contains("employee-search.view","employees.view");
        assertThat(searcher.client.get("/api/lookups").body()).contains("organizational-levels","specialities");
        assertThat(searcher.client.get("/api/scores").statusCode()).isEqualTo(403);
        assertThat(searcher.client.write("POST","/api/employees/search",Map.of(),true).statusCode()).isEqualTo(403);
        assertThat(admin.write("PUT","/api/admin/roles/"+searchRole,Map.of("name","revoked-"+UUID.randomUUID(),"permissions",List.of()),true).statusCode()).isEqualTo(200);
        assertThat(searcher.client.get("/api/employees/search").statusCode()).isEqualTo(403);
        assertThat(new SecurityTestClient(port).get("/api/employees/search").statusCode()).isEqualTo(401);
    }
    @Test void usersWithoutRolesCanOnlyAccessTheirAccountAndCannotEscalate() throws Exception {
        var user=user(List.of());
        assertThat(user.client.get("/api/account").statusCode()).isEqualTo(200);
        assertThat(user.client.get("/api/lookups").body()).isEqualTo("{}");
        for(String path:List.of("/api/employees","/api/summary","/api/admin/users","/api/admin/roles","/access.html","/templates/employees-import.xlsx"))
            assertThat(user.client.get(path).statusCode()).as(path).isEqualTo(403);
        assertThat(user.client.write("POST","/api/admin/roles",Map.of("name","hijack","permissions",List.of("security.manage")),true).statusCode()).isEqualTo(403);
        user.client.write("PUT","/api/account",Map.of("displayName","new","roleIds",List.of(1),"permissions",List.of("security.manage")),true);
        assertThat(user.client.get("/api/admin/users").statusCode()).isEqualTo(403);
    }
    @Test void roleUnionSeparatesOperationsAndRevocationAppliesToExistingSessions() throws Exception {
        long viewer=role("employees.view"),creator=role("companies.create");
        var user=user(List.of(viewer,creator));
        assertThat(user.client.get("/api/employees").statusCode()).isEqualTo(200);
        assertThat(user.client.get("/api/companies").statusCode()).isEqualTo(200);
        var company=user.client.write("POST","/api/companies",Map.of("code",UUID.randomUUID().toString(),"title","test"),true);
        assertThat(company.statusCode()).isEqualTo(201);
        long id=admin.json.readTree(company.body()).get("id").asLong();
        assertThat(user.client.write("DELETE","/api/companies/"+id,null,true).statusCode()).isEqualTo(403);
        assertThat(user.client.write("POST","/api/employees",Map.of(),true).statusCode()).isEqualTo(403);
        assertThat(user.client.write("POST","/api/employees/import/preview",Map.of(),true).statusCode()).isEqualTo(403);
        assertThat(admin.write("PUT","/api/admin/roles/"+viewer,Map.of("name","empty-"+UUID.randomUUID(),"permissions",List.of()),true).statusCode()).isEqualTo(200);
        assertThat(user.client.get("/api/employees").statusCode()).isEqualTo(403);
        assertThat(admin.write("PUT","/api/admin/users/"+user.id,updateUser(true,List.of(viewer)),true).statusCode()).isEqualTo(204);
        assertThat(user.client.get("/api/companies").statusCode()).isEqualTo(403);
    }
    @Test void summaryAndLookupsNeverExposeRestrictedEmployeeProfileFields() throws Exception {
        var user=user(List.of(role("scores.view","dashboard.view")));
        String secret="private-"+UUID.randomUUID();
        assertThat(admin.write("POST","/api/employees",Map.of("personnelCode",UUID.randomUUID().toString(),"firstName","نام","lastName","خانوادگی","description",secret,"birthDate","1370-01-01"),true).statusCode()).isEqualTo(201);
        var lookups=user.client.get("/api/lookups");assertThat(lookups.statusCode()).isEqualTo(200);
        assertThat(lookups.body()).contains("personnelCode").doesNotContain(secret,"birthDate","speciality","educationLevel","grade");
        var summary=admin.json.readTree(user.client.get("/api/summary").body());
        assertThat(summary.has("scores")).isTrue();assertThat(summary.has("employees")).isFalse();
        assertThat(user.client.get("/api/employees").statusCode()).isEqualTo(403);
    }
    @Test void disablingUserRevokesSessionsEvenIfImmediatelyReenabled() throws Exception {
        long viewer=role("employees.view");var user=user(List.of(viewer));
        assertThat(admin.write("PUT","/api/admin/users/"+user.id,updateUser(false,List.of(viewer)),true).statusCode()).isEqualTo(204);
        assertThat(admin.write("PUT","/api/admin/users/"+user.id,updateUser(true,List.of(viewer)),true).statusCode()).isEqualTo(204);
        assertThat(user.client.get("/api/employees").statusCode()).isEqualTo(401);
    }

    @Test void employeeEditorsReceiveReferenceLabelsWithoutReferenceManagementAccess() throws Exception {
        var employeeEditor=user(List.of(role("employees.edit")));
        for(String catalog:List.of("specialities","organizational-levels")) {
            String code=UUID.randomUUID().toString(),title="گزینه "+UUID.randomUUID(),privateNote="private-"+UUID.randomUUID();
            var created=admin.write("POST","/api/"+catalog,Map.of("code",code,"title",title,"description",privateNote),true);
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            long id=admin.json.readTree(created.body()).get("id").asLong();
            assertThat(employeeEditor.client.get("/api/"+catalog).statusCode()).isEqualTo(403);
            var lookup=employeeEditor.client.get("/api/lookups");assertThat(lookup.body()).contains(code,title).doesNotContain(privateNote);
            assertThat(employeeEditor.client.write("POST","/api/"+catalog,Map.of("code",code,"title",title),true).statusCode()).isEqualTo(403);
            assertThat(employeeEditor.client.write("DELETE","/api/"+catalog+"/"+id,null,true).statusCode()).isEqualTo(403);
            long editorRole=role(catalog+".edit");var editor=user(List.of(editorRole));
            assertThat(editor.client.get("/api/"+catalog).statusCode()).isEqualTo(200);
            assertThat(editor.client.write("PUT","/api/"+catalog+"/"+id,Map.of("code",code,"title",title),false).statusCode()).isEqualTo(403);
            assertThat(editor.client.write("PUT","/api/"+catalog+"/"+id,Map.of("code",code,"title",title),true).statusCode()).isEqualTo(200);
            assertThat(editor.client.write("DELETE","/api/"+catalog+"/"+id,null,true).statusCode()).isEqualTo(403);
            assertThat(admin.write("PUT","/api/admin/roles/"+editorRole,Map.of("name","revoked-"+UUID.randomUUID(),"permissions",List.of()),true).statusCode()).isEqualTo(200);
            assertThat(editor.client.get("/api/"+catalog).statusCode()).isEqualTo(403);
        }
    }
    @Test void builtInAndLastActiveAdministratorAreProtectedAndInvalidRolesRollBack() throws Exception {
        long adminRole=jdbc.queryForObject("SELECT id FROM app_roles WHERE system_code = 'ADMIN'",Long.class);
        long adminUser=jdbc.queryForObject("SELECT id FROM app_users WHERE username = 'testuser'",Long.class);
        assertThat(admin.write("DELETE","/api/admin/roles/"+adminRole,null,true).statusCode()).isEqualTo(409);
        assertThat(admin.write("PUT","/api/admin/roles/"+adminRole,Map.of("name","changed","permissions",List.of()),true).statusCode()).isEqualTo(409);
        assertThat(admin.write("PUT","/api/admin/users/"+adminUser,updateUser(false,List.of(adminRole)),true).statusCode()).isEqualTo(409);
        assertThat(admin.write("PUT","/api/admin/users/"+adminUser,updateUser(true,List.of()),true).statusCode()).isEqualTo(409);
        assertThat(admin.get("/api/admin/users").statusCode()).isEqualTo(200);
        var user=user(List.of(role("employees.view")));
        assertThat(admin.write("PUT","/api/admin/users/"+user.id,updateUser(false,List.of(999999L)),true).statusCode()).isEqualTo(400);
        assertThat(user.client.get("/api/employees").statusCode()).isEqualTo(200);
        assertThat(admin.get("/api/admin/users").body()).doesNotContain("passwordHash","password_hash","credentialVersion",SecurityTestClient.PASSWORD);
        assertThat(admin.write("POST","/api/admin/roles",Map.of("name","bad","permissions",List.of("everything")),true).statusCode()).isEqualTo(400);
        assertThat(admin.write("POST","/api/admin/roles",Map.of("name","csrf","permissions",List.of()),false).statusCode()).isEqualTo(403);
    }
    @Test void scoreSheetCannotBypassCreateEditOrDeletePermissionsAndMixedUpdatesAreAtomic() throws Exception {
        var employee=admin.write("POST","/api/employees",Map.of("personnelCode",UUID.randomUUID().toString(),"firstName","نام","lastName","نام"),true);
        long employeeId=admin.json.readTree(employee.body()).get("id").asLong();
        List<Long> factors=new ArrayList<>();
        for(int n=0;n<2;n++)factors.add(admin.json.readTree(admin.write("POST","/api/factors",Map.of("code",UUID.randomUUID().toString(),"title","عامل"),true).body()).get("id").asLong());
        String path="/api/employees/"+employeeId+"/scores";
        admin.write("PUT",path,Map.of("scores",List.of(Map.of("factorId",factors.get(0),"score",50))),true);
        var user=user(List.of(role("scores.create")));
        var mixed=Map.of("scores",List.of(Map.of("factorId",factors.get(1),"score",60),Map.of("factorId",factors.get(0),"score",70)));
        assertThat(user.client.write("PUT",path,mixed,true).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_scores WHERE employee_id = ?",Integer.class,employeeId)).isEqualTo(1);
        var deletion=new LinkedHashMap<String,Object>();deletion.put("factorId",factors.get(0));deletion.put("score",null);
        assertThat(user.client.write("PUT",path,Map.of("scores",List.of(deletion)),true).statusCode()).isEqualTo(403);
        assertThat(user.client.write("PUT",path,Map.of("scores",List.of(Map.of("factorId",factors.get(1),"score",0))),true).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT score FROM employee_scores WHERE employee_id = ? AND factor_id = ?",Integer.class,employeeId,factors.get(0))).isEqualTo(50);
        long id=jdbc.queryForObject("SELECT id FROM employee_scores WHERE employee_id = ? AND factor_id = ?",Long.class,employeeId,factors.get(0));
        assertThat(user.client.write("DELETE","/api/scores/"+id,null,true).statusCode()).isEqualTo(403);
    }
}
