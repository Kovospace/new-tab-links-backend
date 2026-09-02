package com.kovospace.newtablinks.auth.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that registration refuses a password typed two different ways.
 *
 * <p>Worth a test of its own because of what the mistake costs: the person who made it cannot
 * see it - the field shows dots - and the account it creates is one nobody can sign into, so it
 * surfaces days later as a sign-in failure with no explanation. The website checks before
 * submitting, but a client is not a place to enforce a rule, and this endpoint is open to any
 * client that ever registers a user.</p>
 *
 * @since 0.0.5
 */
class RegistrationRequestDtoTest {

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
    @DisplayName("a registration whose two passwords agree is accepted")
    void shouldAcceptMatchingPasswords() {
        assertThat(validate(aRegistrationWith("a long passphrase", "a long passphrase"))).isEmpty();
    }

    @Test
    @DisplayName("a registration whose two passwords differ is refused")
    void shouldRefuseMismatchedPasswords() {
        final Set<ConstraintViolation<RegistrationRequestDto>> violations =
                validate(aRegistrationWith("a long passphrase", "a long passphrasf"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("The password confirmation must match the password");
    }

    @Test
    @DisplayName("a difference of case alone is still a difference")
    void shouldTreatCaseAsSignificant() {
        assertThat(validate(aRegistrationWith("a long passphrase", "A long passphrase")))
                .hasSize(1);
    }

    @Test
    @DisplayName("a missing confirmation is reported once, as a missing field")
    void shouldNotReportAMissingConfirmationTwice() {
        final Set<ConstraintViolation<RegistrationRequestDto>> violations =
                validate(aRegistrationWith("a long passphrase", null));

        // Only @NotBlank speaks. The cross-field rule stays quiet on a null, so the caller is
        // told one thing about one field instead of two things about the same omission.
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("passwordConfirmation");
    }

    /**
     * Builds an otherwise valid registration with the two given passwords.
     *
     * @param password             the chosen password
     * @param passwordConfirmation the repeat of it
     * @return the request to validate
     */
    private static RegistrationRequestDto aRegistrationWith(
            final String password, final String passwordConfirmation) {

        return new RegistrationRequestDto(
                "kovo", "someone@example.com", password, passwordConfirmation, "Matej");
    }

    /**
     * Validates one request.
     *
     * @param registrationRequest the request to validate
     * @return every violated constraint
     */
    private static Set<ConstraintViolation<RegistrationRequestDto>> validate(
            final RegistrationRequestDto registrationRequest) {

        return validator.validate(registrationRequest);
    }
}
