package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One page of accounts.
 *
 * <p>A shape of this project's own rather than Spring Data's {@code Page}. That type serialises
 * to a structure nobody designed - it exposes the paging implementation, its JSON has changed
 * between Spring versions, and Spring Boot warns about serialising it directly. Four numbers and
 * a list is the whole contract a client needs.</p>
 *
 * @param users      the accounts on this page
 * @param page       zero-based index of this page
 * @param size       how many accounts a full page holds
 * @param totalUsers how many accounts match the query in total
 * @param totalPages how many pages that comes to
 * @since 0.0.6
 */
@Schema(description = "One page of user accounts")
public record AdminUserPageDto(
        @Schema(description = "The accounts on this page") List<AdminUserDto> users,
        @Schema(description = "Zero-based index of this page", example = "0") int page,
        @Schema(description = "How many accounts a full page holds", example = "25") int size,
        @Schema(description = "How many accounts match in total", example = "137") long totalUsers,
        @Schema(description = "How many pages that comes to", example = "6") int totalPages) {
}
