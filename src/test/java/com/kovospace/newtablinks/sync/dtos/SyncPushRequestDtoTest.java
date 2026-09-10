package com.kovospace.newtablinks.sync.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies where a pushed batch is refused wholesale and where it is refused one operation at a
 * time, because the two are answered very differently and a client has to know which it is facing.
 *
 * <p>Bean validation runs before any operation is applied, so a single field that does not fit
 * fails the entire request with a 400 and nothing is stored. A field that fits but is missing or
 * empty is not bean validation's business at all - it is checked per operation while the batch
 * runs, which answers 200 and reports that one refusal. The browser extension therefore clamps a
 * profile name to {@value #MAXIMUM_NAME_LENGTH} characters before it builds the operation: an
 * over-long name typed into its rename dialog would otherwise cost the user every other change in
 * the same push.</p>
 *
 * @since 0.0.7
 */
class SyncPushRequestDtoTest {

    /** Longest name any record may carry, matching the column the name is stored in. */
    private static final int MAXIMUM_NAME_LENGTH = 120;

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    /**
     * Builds the validator once for the whole class.
     */
    @BeforeAll
    static void buildValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    /**
     * Releases the validator factory.
     */
    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("a profile name of exactly the maximum length is accepted")
    void shouldAcceptANameOfTheMaximumLength() {
        assertThat(validate(aPushOf(upsertProfileNamed(nameOfLength(MAXIMUM_NAME_LENGTH)))))
                .isEmpty();
    }

    @Test
    @DisplayName("one over-long name fails the whole batch, not just its own operation")
    void shouldFailTheWholeBatchForOneOverLongName() {

        final Set<ConstraintViolation<SyncPushRequestDto>> violations = validate(aPushOf(
                upsertProfileNamed("Work"),
                upsertProfileNamed(nameOfLength(MAXIMUM_NAME_LENGTH + 1))));

        // The violation is on the request, so the controller never reaches SyncPushService and the
        // acceptable first operation is discarded along with the second.
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("operations[1].name");
    }

    @Test
    @DisplayName("a blank name is not bean validation's business, so the batch still runs")
    void shouldLeaveABlankNameToThePerOperationCheck() {

        // No @NotBlank here on purpose: one flat shape covers five kinds of record, and which
        // fields a kind needs is checked per operation, where one can be refused on its own.
        assertThat(validate(aPushOf(upsertProfileNamed("   ")))).isEmpty();
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Builds a name of a given length.
     *
     * @param length number of characters
     * @return a name of exactly that length
     */
    private static String nameOfLength(final int length) {
        return "n".repeat(length);
    }

    /**
     * Wraps operations into a batch from one device.
     *
     * @param operations the operations, in the order they were made
     * @return the request to validate
     */
    private static SyncPushRequestDto aPushOf(final SyncOperationDto... operations) {
        return new SyncPushRequestDto("a-device", List.of(operations));
    }

    /**
     * Builds a pushed profile upsert - the shape the extension sends a rename as.
     *
     * @param name name to store
     * @return the operation
     */
    private static SyncOperationDto upsertProfileNamed(final String name) {
        return new SyncOperationDto(
                SyncOperationKind.UPSERT, SyncEntityKind.PROFILE, UUID.randomUUID(),
                null, null, null, null,
                name, null, null, null, null,
                null, null, null, null, null, null, 0);
    }

    /**
     * Validates one batch.
     *
     * @param pushRequest the request to validate
     * @return every violated constraint
     */
    private static Set<ConstraintViolation<SyncPushRequestDto>> validate(
            final SyncPushRequestDto pushRequest) {

        return validator.validate(pushRequest);
    }
}
