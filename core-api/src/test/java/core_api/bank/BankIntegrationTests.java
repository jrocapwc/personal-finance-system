package core_api.bank;
import core_api.*;
import core_api.category.*;
import core_api.user.*;
import core_api.transaction.*;
import core_api.integration.*;
import core_api.security.TokenService;
import core_api.budget.*;
import core_api.dashboard.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class BankIntegrationTests extends JwtTestSupport {
 @Autowired BankService banks;
 @Autowired UserRepository users;
 @Autowired CategoryService categories;
 @Autowired BudgetService budgets;
 @Autowired DashboardService dashboard;
 @Autowired TokenService tokens;
 @Autowired JdbcTemplate db;
 @MockitoBean MockTransactionClient provider;
 @LocalServerPort int port;
 final JsonMapper json=JsonMapper.builder().build();
 User account(){ var u=users.saveAndFlush(new User("Bank test",UUID.randomUUID()+"@example.com","dummy-hash"));categories.list(u.getId(),null);return u; }
 Long food(User u){return categories.list(u.getId(),CategoryType.EXPENSE).stream().filter(c->c.name().equals("Food")).findFirst().orElseThrow().id();}
 byte[] csv(String rows){return ("Date,Description,Amount,Reference\n"+rows).getBytes(StandardCharsets.UTF_8);}
 StatementCsv.Mapping mapping(boolean ref){return new StatementCsv.Mapping(0,1,2,-1,-1,ref?3:-1,-1,"ISO","checking");}
 BankService.Preview preview(User u,boolean ref){return banks.preview(u.getId(),csv("2026-10-01,Fictional food,-12.34,one\n2026-10-02,Fictional food,-12.34,two"),mapping(ref));}
 BankService.Confirmation approve(BankService.Preview p,Long c){return new BankService.Confirmation(p.rows().stream().map(r->new BankService.Selection(r.rowNumber(),c)).toList());}
 @Test void connectionStateIsPersistentAndOwned(){var a=account();var b=account();assertThat(banks.connection(a.getId()).connected()).isFalse();assertThat(banks.connect(a.getId(),true).simulated()).isTrue();assertThat(banks.connection(a.getId()).connected()).isTrue();assertThat(banks.connection(b.getId()).connected()).isFalse();banks.connect(a.getId(),false);assertThat(banks.connection(a.getId()).connected()).isFalse();}
 @Test void previewDoesNotImportConfirmationIsIdempotentAndReportsRefresh(){
  var u=account();var c=food(u);var b=budgets.create(u.getId(),new BudgetRequest(c,10,2026,new BigDecimal("100.00"),80));var p=preview(u,false);
  assertThat(dashboard.get(u.getId(),10,2026).summary().totalExpenses()).isZero();var result=banks.confirm(u.getId(),p.id(),approve(p,c));assertThat(result.importedCount()).isEqualTo(2);
  assertThat(banks.confirm(u.getId(),p.id(),approve(p,c))).isEqualTo(result);assertThat(dashboard.get(u.getId(),10,2026).summary().totalExpenses()).isEqualByComparingTo("24.68");assertThat(budgets.get(u.getId(),b.id()).spentAmount()).isEqualByComparingTo("24.68");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM statement_rows WHERE batch_id=?",Integer.class,p.id())).isZero();var repeat=preview(u,false);assertThat(repeat.rows()).allMatch(BankService.PreviewRow::duplicate);assertThat(banks.confirm(u.getId(),repeat.id(),approve(repeat,c)).importedCount()).isZero();
 }
 @Test void referencesDeduplicateOverlapsAndPreserveIdenticalLegitimateRows(){var u=account();var p=preview(u,true);var c=food(u);assertThat(banks.confirm(u.getId(),p.id(),approve(p,c)).importedCount()).isEqualTo(2);var overlap=banks.preview(u.getId(),csv("2026-10-02,Fictional food,-12.34,two\n2026-10-02,Fictional food,-12.34,three"),mapping(true));assertThat(overlap.rows().get(0).duplicate()).isTrue();assertThat(overlap.rows().get(1).duplicate()).isFalse();assertThat(banks.confirm(u.getId(),overlap.id(),approve(overlap,c)).importedCount()).isEqualTo(1);}
 @Test void ownershipAndCategoriesRevalidatedWithAtomicRollback(){var a=account();var b=account();var p=preview(a,true);assertThatThrownBy(()->banks.confirm(b.getId(),p.id(),approve(p,food(b)))).isInstanceOf(BankException.class);assertThatThrownBy(()->banks.cancel(b.getId(),p.id())).isInstanceOf(BankException.class);var choices=new BankService.Confirmation(List.of(new BankService.Selection(1,food(a)),new BankService.Selection(2,food(b))));assertThatThrownBy(()->banks.confirm(a.getId(),p.id(),choices)).isInstanceOf(BankException.class);assertThat(dashboard.get(a.getId(),10,2026).summary().totalExpenses()).isZero();assertThat(banks.confirm(a.getId(),p.id(),approve(p,food(a))).importedCount()).isEqualTo(2);assertThat(preview(b,true).rows()).noneMatch(BankService.PreviewRow::duplicate);}
 @Test void cancellationExpirationAndReplacementRemovePreviewData(){var u=account();var p=preview(u,false);banks.cancel(u.getId(),p.id());assertThatThrownBy(()->banks.confirm(u.getId(),p.id(),approve(p,food(u)))).isInstanceOf(BankException.class);var second=preview(u,false);var third=preview(u,false);assertThatThrownBy(()->banks.confirm(u.getId(),second.id(),approve(second,food(u)))).isInstanceOf(BankException.class);db.update("UPDATE statement_imports SET expires_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) WHERE id=?",third.id());assertThatThrownBy(()->banks.confirm(u.getId(),third.id(),approve(third,food(u)))).isInstanceOf(BankException.class);banks.purgeExpired();assertThat(db.queryForObject("SELECT COUNT(*) FROM statement_rows WHERE batch_id=?",Integer.class,third.id())).isZero();}
 @Test void invalidRowsUnknownRowsAndTypeMismatchRejected(){var u=account();var p=banks.preview(u.getId(),csv("2026-02-30,Invalid,-12.34,one"),mapping(true));assertThat(p.rows().getFirst().validationError()).isNotNull();assertThatThrownBy(()->banks.confirm(u.getId(),p.id(),approve(p,food(u)))).isInstanceOf(BankException.class);var valid=preview(u,true);assertThatThrownBy(()->banks.confirm(u.getId(),valid.id(),new BankService.Confirmation(List.of(new BankService.Selection(999,food(u)))))).isInstanceOf(BankException.class);var income=categories.list(u.getId(),CategoryType.INCOME).getFirst().id();assertThatThrownBy(()->banks.confirm(u.getId(),valid.id(),approve(valid,income))).isInstanceOf(BankException.class);}
 @Test void concurrentConfirmationCreatesOneSet() throws Exception{var u=account();var p=preview(u,true);var choices=approve(p,food(u));try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var a=executor.submit(()->banks.confirm(u.getId(),p.id(),choices));var b=executor.submit(()->banks.confirm(u.getId(),p.id(),choices));assertThat(a.get()).isEqualTo(b.get());}assertThat(dashboard.get(u.getId(),10,2026).summary().totalExpenses()).isEqualByComparingTo("24.68");}
 @Test void malformedOversizedAndWrongEncodingMultipartFilesRejected() throws Exception {
  var u=account();String boundary="limits-test";
  for(var fixture:List.of(new Object[]{"a,b\n\"unfinished,x",400},new Object[]{"x".repeat(StatementCsv.MAX_BYTES+1),413},new Object[]{"a,b\nx,\u0000",400})) {
   String content=(String)fixture[0];int status=(Integer)fixture[1];
   String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"demo.csv\"\r\nContent-Type: text/csv\r\n\r\n"+content+"\r\n--"+boundary+"--\r\n";
   try(var client=HttpClient.newHttpClient()) {
    var response=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/bank-connections/statements/columns")).header("Authorization","Bearer "+tokens.issue(u.getId())).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
   }
  }
 }
 String base(){return "http://127.0.0.1:"+port;}
 HttpResponse<String> request(User u,String method,String path,String body) throws Exception{var builder=HttpRequest.newBuilder(URI.create(base()+"/api/v1/bank-connections"+path));if(u!=null)builder.header("Authorization","Bearer "+tokens.issue(u.getId()));if(body!=null)builder.header("Content-Type","application/json");try(var client=HttpClient.newHttpClient()){return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}}
 @Test void syncRequiresConnectionRepeatsSkipAndDisconnectKeepsTransactions() throws Exception{
  var u=account();when(provider.fetch()).thenReturn(List.of(json.readTree("{\"externalTransactionId\":\"bank-test\",\"type\":\"EXPENSE\",\"amount\":\"12.34\",\"description\":\"Fictional food\",\"transactionDate\":\"2026-10-01\",\"categoryHint\":\"FOOD\",\"source\":\"MOCK_PROVIDER\"}")));
  assertThat(request(u,"POST","/demo/sync",null).statusCode()).isEqualTo(409);assertThat(request(u,"POST","/demo",null).statusCode()).isEqualTo(200);var first=request(u,"POST","/demo/sync",null);assertThat(first.statusCode()).isEqualTo(200);assertThat(json.readTree(first.body()).path("importedCount").asInt()).isEqualTo(1);assertThat(json.readTree(request(u,"POST","/demo/sync",null).body()).path("skippedCount").asInt()).isEqualTo(1);assertThat(banks.connection(u.getId()).lastSync()).isNotNull();assertThat(request(u,"DELETE","/demo",null).statusCode()).isEqualTo(200);assertThat(request(u,"POST","/demo/sync",null).statusCode()).isEqualTo(409);assertThat(dashboard.get(u.getId(),10,2026).summary().totalExpenses()).isEqualByComparingTo("12.34");
 }
 @Test void multipartMappingAndConfirmationUseServerHeldRows() throws Exception{
  var u=account();String boundary="bank-test-boundary";var body=new StringBuilder();Map<String,String> params=Map.of("date","0","description","1","amount","2","debit","-1","credit","-1","reference","3","currency","-1","dateFormat","ISO","account","checking");params.forEach((key,value)->body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n").append(value).append("\r\n"));body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fictional.csv\"\r\nContent-Type: text/csv\r\n\r\nDate,Description,Amount,Reference\n2026-10-01,Food,-12.34,ref-1\r\n--").append(boundary).append("--\r\n");try(var client=HttpClient.newHttpClient()){var response=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/bank-connections/statements/preview")).header("Authorization","Bearer "+tokens.issue(u.getId())).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);String id=json.readTree(response.body()).path("id").asString();var confirmed=request(u,"POST","/statements/"+id+"/confirm","{\"rows\":[{\"rowNumber\":1,\"categoryId\":"+food(u)+"}]}");assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);assertThat(request(u,"POST","/statements/"+id+"/confirm","{\"userId\":\"other\",\"rows\":[]}").statusCode()).isEqualTo(400);}
 }
 @Test void cookieRequestsRejectMissingInvalidCsrfAndMissingSession() throws Exception{var u=account();assertThat(request(null,"GET","",null).statusCode()).isEqualTo(401);try(var client=HttpClient.newHttpClient()){for(String path:List.of("/demo","/demo/sync","/statements/columns","/statements/preview","/statements/"+UUID.randomUUID()+"/confirm")){var response=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/bank-connections"+path)).header("Cookie","__Host-finance_session="+tokens.issue(u.getId())).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(403);}var invalid=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/bank-connections/demo")).header("Cookie","__Host-finance_session="+tokens.issue(u.getId())+"; XSRF-TOKEN=abc").header("X-XSRF-TOKEN","wrong").POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertThat(invalid.statusCode()).isEqualTo(403);}}
}
