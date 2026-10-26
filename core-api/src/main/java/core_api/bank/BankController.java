package core_api.bank;

import core_api.error.ApiError;
import core_api.integration.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Bank Connections", description = "Simulated banking and reviewed PHP CSV imports; cookie mutations require CSRF")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/bank-connections")
public class BankController {
    private final BankService banks;
    private final MockImportService imports;
    public BankController(BankService banks, MockImportService imports) { this.banks=banks; this.imports=imports; }
    private UUID owner(Authentication auth) { return UUID.fromString(auth.getName()); }
    @GetMapping public BankService.Connection connection(Authentication auth) { return banks.connection(owner(auth)); }
    @PostMapping("/demo") public BankService.Connection connect(Authentication auth) { return banks.connect(owner(auth),true); }
    @DeleteMapping("/demo") public BankService.Connection disconnect(Authentication auth) { return banks.connect(owner(auth),false); }
    @PostMapping("/demo/sync") public MockImportService.ImportResult sync(Authentication auth) {
        UUID owner = owner(auth);
        if (!banks.connection(owner).connected()) throw new BankException(409,"Connect the demo account before synchronizing");
        var result = imports.importFor(owner); banks.synced(owner); return result;
    }
    @PostMapping(value="/statements/columns", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public java.util.List<String> columns(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        if (file.getSize()>StatementCsv.MAX_BYTES) throw new BankException(413,"Maximum CSV size is 1 MiB");
        return StatementCsv.parse(file.getBytes()).getFirst();
    }
    @PostMapping(value="/statements/preview", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BankService.Preview preview(Authentication auth, @RequestParam("file") MultipartFile file,
            @ModelAttribute StatementCsv.Mapping mapping) throws java.io.IOException {
        if (file.getSize()>StatementCsv.MAX_BYTES) throw new BankException(413,"Maximum CSV size is 1 MiB");
        return banks.preview(owner(auth),file.getBytes(),mapping);
    }
    @PostMapping("/statements/{id}/confirm") public BankService.Result confirm(Authentication auth,@PathVariable UUID id,@RequestBody BankService.Confirmation request) {
        return banks.confirm(owner(auth),id,request);
    }
    @DeleteMapping("/statements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Authentication auth,@PathVariable UUID id) { banks.cancel(owner(auth),id); }
    @ExceptionHandler(BankException.class) ResponseEntity<ApiError> error(BankException e,HttpServletRequest request) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.status(),"BANK_IMPORT_ERROR",e.getMessage(),request.getRequestURI()));
    }
    @ExceptionHandler(IntegrationException.class) ResponseEntity<ApiError> integration(IntegrationException e,HttpServletRequest request) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.status(),e.code(),e.getMessage(),request.getRequestURI()));
    }
}
