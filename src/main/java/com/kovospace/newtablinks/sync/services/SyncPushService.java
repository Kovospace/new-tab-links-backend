package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.closedtab.services.ClosedTabSynchronizationService;
import com.kovospace.newtablinks.common.exceptions.FairUseLimitReachedException;
import com.kovospace.newtablinks.common.models.FairUseCounts;
import com.kovospace.newtablinks.common.services.FairUseLimitGuard;
import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import com.kovospace.newtablinks.sync.dtos.SyncOperationDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushRequestDto;
import com.kovospace.newtablinks.sync.dtos.SyncPushResultDto;
import com.kovospace.newtablinks.sync.dtos.SyncRejectedOperationDto;
import com.kovospace.newtablinks.sync.events.UserDataChangePublisher;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import com.kovospace.newtablinks.sync.models.SyncOperationContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a batch of changes a client made while it was the only one that knew about them.
 *
 * <p>The counterpart of {@link SyncSnapshotService}, and the half of synchronization that used to
 * be missing. Operations are applied strictly in the order they arrive, in one transaction. The
 * client is responsible for that order - upserts parent before child, deletes child before
 * parent - because it is the only side that knows what it did and in what sequence.</p>
 *
 * <p>A push is not all-or-nothing at the level of individual operations. One that names a parent
 * this account no longer has is refused and reported, and the batch carries on: a device coming
 * back after a week must not lose the rest of its work to one stale reference. It <em>is</em>
 * all-or-nothing at the level of the transaction, so a genuine failure leaves the account exactly
 * as it was.</p>
 *
 * <p>The Fair Use Policy is judged on the batch as a whole, not per operation: the account is
 * locked and counted before the first operation and counted again after the last, and a batch
 * that left a profile, workspace or links-per-workspace count both above its cap and larger than
 * it started fails entirely with {@link FairUseLimitReachedException} (HTTP 409), leaving the
 * account as it was. Per operation would refuse a device that replaces a record at a cap, since a
 * device sends its upserts before its deletions. Closed-tab history never fails a push: the
 * oldest entries beyond its cap are deleted instead.</p>
 *
 * @since 0.0.6
 */
@Service
public class SyncPushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncPushService.class);

    private final Map<SyncEntityKind, SyncOperationApplier> appliersByEntityKind =
            new EnumMap<>(SyncEntityKind.class);

    private final UserDataChangePublisher userDataChangePublisher;
    private final FairUseLimitGuard fairUseLimitGuard;
    private final ClosedTabSynchronizationService closedTabSynchronizationService;

    /**
     * Creates the service.
     *
     * @param appliers                every applier Spring found, one per kind of record
     * @param userDataChangePublisher         announces the change to the user's other browsers
     * @param fairUseLimitGuard               refuses a batch that grows the account past a cap
     * @param closedTabSynchronizationService trims closed-tab history to its cap
     */
    public SyncPushService(
            final List<SyncOperationApplier> appliers,
            final UserDataChangePublisher userDataChangePublisher,
            final FairUseLimitGuard fairUseLimitGuard,
            final ClosedTabSynchronizationService closedTabSynchronizationService) {

        appliers.forEach(applier ->
                appliersByEntityKind.put(applier.supportedEntityKind(), applier));
        this.userDataChangePublisher = userDataChangePublisher;
        this.fairUseLimitGuard = fairUseLimitGuard;
        this.closedTabSynchronizationService = closedTabSynchronizationService;
    }

    /**
     * Applies a pushed batch and reports what happened to it.
     *
     * <p>The refresh notification is published before the first operation rather than after the
     * last, so that the notification naming the device that caused the change is the one that
     * survives: the publisher keeps the first announcement made in a transaction and discards the
     * rest, and every applied operation announces one of its own without a device name. Nothing
     * is delivered until the transaction commits, so announcing early cannot tell anyone about a
     * change that did not happen.</p>
     *
     * @param pushRequest the batch to apply
     * @param ownerId     identifier of the user it belongs to, taken from the access token
     * @return the identifiers that had to be remapped and the operations that were refused
     * @throws FairUseLimitReachedException when the batch would leave a capped collection above
     *                                      its cap and larger than before; nothing is applied
     */
    @Transactional
    public SyncPushResultDto applyPushedOperations(
            final SyncPushRequestDto pushRequest,
            final UUID ownerId) {

        final List<SyncOperationDto> operations = pushRequest.operations();
        final SyncOperationContext context = new SyncOperationContext(ownerId);
        final List<SyncRejectedOperationDto> rejectedOperations = new ArrayList<>();

        if (operations.isEmpty()) {
            return new SyncPushResultDto(Instant.now(), List.of(), List.of());
        }
        userDataChangePublisher.publishChangeFor(ownerId, pushRequest.originDeviceId());
        final FairUseCounts countsBeforeBatch = fairUseLimitGuard.captureCountsBeforeBatch(ownerId);

        for (int index = 0; index < operations.size(); index++) {
            applyOneOperation(operations.get(index), index, context, rejectedOperations);
        }

        closedTabSynchronizationService.trimHistoryToFairUseCap(ownerId);
        fairUseLimitGuard.requireBatchDidNotGrowPastCaps(ownerId, countsBeforeBatch);

        LOGGER.debug("Applied {} pushed operations for account {}, {} remapped, {} refused",
                operations.size(), ownerId, context.getRemappings().size(),
                rejectedOperations.size());

        return new SyncPushResultDto(
                Instant.now(), context.getRemappings(), List.copyOf(rejectedOperations));
    }

    /**
     * Applies a single operation, recording a refusal instead of letting it end the batch.
     *
     * @param operation          the operation to apply
     * @param index              its position in the request array, reported back to the client
     * @param context            the push it is part of
     * @param rejectedOperations collector the refusal is added to
     */
    private void applyOneOperation(
            final SyncOperationDto operation,
            final int index,
            final SyncOperationContext context,
            final List<SyncRejectedOperationDto> rejectedOperations) {

        try {
            dispatchOperation(operation, context);
        } catch (final SyncOperationRejectedException rejection) {
            rejectedOperations.add(new SyncRejectedOperationDto(
                    index, operation.entityKind(), operation.id(), rejection.getReason()));
        }
    }

    /**
     * Hands one operation to the applier that owns its kind of record.
     *
     * @param operation the operation to apply
     * @param context   the push it is part of
     */
    private void dispatchOperation(
            final SyncOperationDto operation,
            final SyncOperationContext context) {

        final SyncOperationApplier applier = appliersByEntityKind.get(operation.entityKind());

        switch (operation.operation()) {
            case UPSERT -> context.recordRemapping(
                    operation.entityKind(),
                    operation.id(),
                    applier.applyUpsert(operation, context));
            case DELETE -> applier.applyDelete(operation, context);
        }
    }
}
