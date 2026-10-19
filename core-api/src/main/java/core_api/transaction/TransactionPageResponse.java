package core_api.transaction;

import org.springframework.data.domain.Page;

import java.util.List;

public record TransactionPageResponse(List<TransactionResponse> content, int page, int size,
                                      long totalElements, int totalPages, boolean first, boolean last,
                                      String sort, String direction) {
    public static TransactionPageResponse from(Page<Transaction> result, TransactionQuery query) {
        return new TransactionPageResponse(result.map(TransactionResponse::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast(),
                query.sort(), query.direction());
    }
}
