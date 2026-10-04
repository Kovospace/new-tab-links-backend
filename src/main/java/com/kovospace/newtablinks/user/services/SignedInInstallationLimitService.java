package com.kovospace.newtablinks.user.services;

import com.kovospace.newtablinks.auth.repositories.RefreshTokenRepository;
import com.kovospace.newtablinks.common.exceptions.PlanLimitReachedException;
import com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException;
import com.kovospace.newtablinks.common.models.EffectivePlanLimits;
import com.kovospace.newtablinks.common.models.PlanLimit;
import com.kovospace.newtablinks.common.services.PlanLimitPolicy;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds an account to its plan's number of extension installations signed in at once.
 *
 * <p>Counted are installations signed in <em>now</em> - a device that reported an installation
 * identifier and holds a refresh token neither revoked nor expired - never device rows: the device
 * list keeps its history after a sign-out. The website's own sign-ins report no installation and
 * never count.</p>
 *
 * <p>Two moments apply the limit:</p>
 * <ul>
 *   <li><strong>Sign-in</strong> ({@link #requireRoomToSignInAgain},
 *       {@link #requireRoomForNewInstallation}): an installation that is not signed in already
 *       is refused when the others fill the limit. The user signs another installation out on
 *       the devices page ({@code manageUrl}), or upgrades.</li>
 *   <li><strong>Token refresh</strong> ({@link #signOutWhenBeyondLimit}): when the limit has
 *       dropped below the number signed in - premium ended - the installations beyond it, in the
 *       order they first signed in (the device row's {@code createdAt}), are signed out the next
 *       time they refresh. An access token lives fifteen minutes, so that is how long one of them
 *       keeps working. Nothing runs on the downgrade itself; there is no event to run on.</li>
 * </ul>
 *
 * @since 0.0.18
 */
@Service
public class SignedInInstallationLimitService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SignedInInstallationLimitService.class);

    private static final String ACCOUNT_RESOURCE_NAME = "User";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PlanLimitPolicy planLimitPolicy;

    /**
     * Creates the service.
     *
     * @param refreshTokenRepository tells which installations hold a live session, and signs one
     *                               out
     * @param userRepository         locks the account row
     * @param planLimitPolicy        the account's limits and the wording of a refusal
     */
    public SignedInInstallationLimitService(
            final RefreshTokenRepository refreshTokenRepository,
            final UserRepository userRepository,
            final PlanLimitPolicy planLimitPolicy) {

        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.planLimitPolicy = planLimitPolicy;
    }

    /**
     * Refuses an installation the account already knows signing in again, when it is not in
     * session now and the others fill the limit.
     *
     * <p>One already signed in passes without counting - signing in again adds nothing.</p>
     *
     * @param knownInstallation the installation's device row, recorded by an earlier sign-in
     * @throws PlanLimitReachedException with {@link PlanLimit#DEVICES} when the other signed-in
     *                                   installations already fill the limit; the caller's
     *                                   transaction rolls back and no tokens are issued
     * @throws ResourceNotFoundException when the account does not exist
     */
    @Transactional
    public void requireRoomToSignInAgain(final UserDeviceEntity knownInstallation) {
        final UUID accountId = lockAccount(knownInstallation);
        if (refreshTokenRepository.existsLiveTokenOfDevice(knownInstallation.getId(), Instant.now())) {
            return;
        }
        requireRoomBesides(accountId, knownInstallation);
    }

    /**
     * Refuses an installation signing in for the first time - on a new row, or on a row the
     * website recorded by name that it claims - when the others fill the limit.
     *
     * <p>Always counted, even when the row already holds a live session: a claimed row's session
     * is the website's, which was never an installation's and never counted.</p>
     *
     * @param newInstallation the device row the installation now holds, already stored
     * @throws PlanLimitReachedException with {@link PlanLimit#DEVICES} when the other signed-in
     *                                   installations already fill the limit; the caller's
     *                                   transaction rolls back and no tokens are issued
     * @throws ResourceNotFoundException when the account does not exist
     */
    @Transactional
    public void requireRoomForNewInstallation(final UserDeviceEntity newInstallation) {
        requireRoomBesides(lockAccount(newInstallation), newInstallation);
    }

    /**
     * Signs an installation out, and refuses its token refresh, when it lies beyond the plan's
     * limit in the order the account's signed-in installations first signed in.
     *
     * <p>The sign-out must survive the refusal, so this method - and the caller's transaction -
     * do not roll back for {@link PlanLimitReachedException}.</p>
     *
     * @param device the device refreshing its session
     * @throws PlanLimitReachedException with {@link PlanLimit#DEVICES} after revoking every live
     *                                   token of the device
     */
    @Transactional(noRollbackFor = PlanLimitReachedException.class)
    public void signOutWhenBeyondLimit(final UserDeviceEntity device) {
        if (device.getInstallationId() == null) {
            return;
        }
        final UUID accountId = device.getUser().getId();
        final Instant now = Instant.now();
        final EffectivePlanLimits limits = planLimitPolicy.effectiveLimitsFor(accountId);
        final long signedInEarlier = refreshTokenRepository
                .countSignedInInstallationsFirstSignedInBefore(
                        accountId, device.getCreatedAt(), now);
        if (signedInEarlier < limits.maximumFor(PlanLimit.DEVICES)) {
            return;
        }
        final int revoked = refreshTokenRepository.revokeAllLiveTokensOfDevice(device.getId(), now);
        LOGGER.info("Signed out installation {} of account {} beyond the plan's {} signed-in "
                        + "installations, revoking {} token(s)",
                device.getId(), accountId, limits.maximumFor(PlanLimit.DEVICES), revoked);
        throw planLimitPolicy.refusalOf(limits, PlanLimit.DEVICES);
    }

    /**
     * Locks the account row, so two installations racing for the last place queue and the
     * second is refused.
     *
     * @param device a device of the account
     * @return the account's identifier
     * @throws ResourceNotFoundException when the account does not exist
     */
    private UUID lockAccount(final UserDeviceEntity device) {
        final UUID accountId = device.getUser().getId();
        userRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_RESOURCE_NAME, accountId));
        return accountId;
    }

    /**
     * Refuses one more installation in session when the others already fill the limit.
     *
     * @param accountId identifier of the account
     * @param device    the device signing in, left out of the count
     * @throws PlanLimitReachedException with {@link PlanLimit#DEVICES}
     */
    private void requireRoomBesides(final UUID accountId, final UserDeviceEntity device) {
        final EffectivePlanLimits limits = planLimitPolicy.effectiveLimitsFor(accountId);
        final long othersSignedIn = refreshTokenRepository
                .countSignedInInstallationsOtherThan(accountId, device.getId(), Instant.now());
        if (othersSignedIn + 1 > limits.maximumFor(PlanLimit.DEVICES)) {
            throw planLimitPolicy.refusalOf(limits, PlanLimit.DEVICES);
        }
    }
}
