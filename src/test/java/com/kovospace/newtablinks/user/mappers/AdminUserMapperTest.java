package com.kovospace.newtablinks.user.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.entitlement.models.ProStanding;
import com.kovospace.newtablinks.user.dtos.AdminUserDto;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins that the operator's account list carries each account's own pro standing, in order.
 *
 * @since 0.0.10
 */
class AdminUserMapperTest {

    private static final UUID PAID_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GRANTED_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAIN_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static final Instant GRANTED_UNTIL = Instant.parse("2027-10-04T12:00:00Z");

    private final AdminUserMapper adminUserMapper = new AdminUserMapperImpl();

    @Test
    @DisplayName("each account on a page carries premium, its source and end, or false and null")
    void shouldCarryEachAccountsProStanding() {
        final List<UserEntity> accounts = List.of(
                account(PAID_ACCOUNT_ID, "paid"),
                account(GRANTED_ACCOUNT_ID, "granted"),
                account(PLAIN_ACCOUNT_ID, "plain"));
        final Map<UUID, ProStanding> standing = Map.of(
                PAID_ACCOUNT_ID, new ProStanding(true, EntitlementSource.LIFETIME, null),
                GRANTED_ACCOUNT_ID, new ProStanding(true, EntitlementSource.GRANT, GRANTED_UNTIL),
                PLAIN_ACCOUNT_ID, ProStanding.NOT_PRO);

        final List<AdminUserDto> rendered = adminUserMapper.toDtoList(accounts, standing);

        assertThat(rendered).extracting(AdminUserDto::username)
                .containsExactly("paid", "granted", "plain");
        assertThat(rendered).extracting(AdminUserDto::premium).containsExactly(true, true, false);
        assertThat(rendered).extracting(AdminUserDto::premiumSource)
                .containsExactly(EntitlementSource.LIFETIME, EntitlementSource.GRANT, null);
        assertThat(rendered).extracting(AdminUserDto::premiumUntil)
                .containsExactly(null, GRANTED_UNTIL, null);
    }

    @Test
    @DisplayName("an account missing from the standing map is rendered as not pro")
    void shouldRenderAnAccountWithoutStandingAsNotPro() {
        final AdminUserDto rendered = adminUserMapper
                .toDtoList(List.of(account(PLAIN_ACCOUNT_ID, "plain")), Map.of())
                .getFirst();

        assertThat(rendered.premium()).isFalse();
        assertThat(rendered.premiumSource()).isNull();
        assertThat(rendered.premiumUntil()).isNull();
        assertThat(rendered.id()).isEqualTo(PLAIN_ACCOUNT_ID);
    }

    /**
     * An account with a fixed identifier.
     *
     * @param id       its identifier
     * @param username its username
     * @return the account
     */
    private static UserEntity account(final UUID id, final String username) {
        final UserEntity account = new UserEntity(
                username, username + "@example.com", null, username, UserAccountStatus.ACTIVE);
        account.setId(id);
        return account;
    }
}
