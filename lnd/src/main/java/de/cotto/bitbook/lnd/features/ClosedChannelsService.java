package de.cotto.bitbook.lnd.features;

import de.cotto.bitbook.backend.AddressDescriptionService;
import de.cotto.bitbook.backend.TransactionDescriptionService;
import de.cotto.bitbook.lnd.model.ClosedChannel;
import de.cotto.bitbook.lnd.model.Initiator;
import de.cotto.bitbook.lnd.model.Resolution;
import de.cotto.bitbook.ownership.AddressOwnershipService;
import de.cotto.bitbook.ownership.OwnershipStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClosedChannelsService {
    private static final String DEFAULT_ADDRESS_DESCRIPTION = "lnd";

    private final TransactionDescriptionService transactionDescriptionService;
    private final AddressDescriptionService addressDescriptionService;
    private final AddressOwnershipService addressOwnershipService;
    private final SweepTransactionsService sweepTransactionsService;

    public ClosedChannelsService(
            TransactionDescriptionService transactionDescriptionService,
            AddressDescriptionService addressDescriptionService,
            AddressOwnershipService addressOwnershipService,
            SweepTransactionsService sweepTransactionsService
    ) {
        this.transactionDescriptionService = transactionDescriptionService;
        this.addressDescriptionService = addressDescriptionService;
        this.addressOwnershipService = addressOwnershipService;
        this.sweepTransactionsService = sweepTransactionsService;
    }

    public long addFromClosedChannels(Set<ClosedChannel> closedChannels) {
        return closedChannels.parallelStream()
                .filter(ClosedChannel::isValid)
                .map(this::addFromClosedChannel)
                .count();
    }

    private ClosedChannel addFromClosedChannel(ClosedChannel closedChannel) {
        String channelAddress = closedChannel.getChannelAddress();
        String remotePubkey = closedChannel.getRemotePubkey();

        setTransactionDescriptions(closedChannel);
        setForSettlementAddress(closedChannel);
        setChannelAddressOwnershipAndDescription(channelAddress, closedChannel.getOpenInitiator(), remotePubkey);
        addFromHtlcSweepTransactions(closedChannel);
        return closedChannel;
    }

    private void addFromHtlcSweepTransactions(ClosedChannel closedChannel) {
        Set<String> sweepTransactionHashes = closedChannel.getResolutions().stream()
                .map(Resolution::getSweepTransactionHash)
                .filter(sweepTransactonHash -> !sweepTransactonHash.isBlank())
                .collect(Collectors.toSet());
        sweepTransactionsService.addFromSweeps(sweepTransactionHashes);
    }

    private void setTransactionDescriptions(ClosedChannel closedChannel) {
        String remotePubkey = closedChannel.getRemotePubkey();
        transactionDescriptionService.set(
                closedChannel.getOpeningTransaction().getHash(),
                "Opening Channel with %s (%s)".formatted(remotePubkey, closedChannel.getOpenInitiator())
        );
        transactionDescriptionService.set(
                closedChannel.getClosingTransaction().getHash(),
                "Closing Channel with %s (%s)".formatted(remotePubkey, closedChannel.getCloseType())
        );
    }

    private void setForSettlementAddress(ClosedChannel closedChannel) {
        closedChannel.getSettlementAddress().ifPresent(address -> {
            addressOwnershipService.setAddressAsOwned(address);
            addressDescriptionService.set(address, DEFAULT_ADDRESS_DESCRIPTION);
        });
    }

    private void setChannelAddressOwnershipAndDescription(
            String channelAddress,
            Initiator openInitiator,
            String remotePubkey
    ) {
        setChannelAddressDescription(channelAddress, remotePubkey);
        setChannelAddressOwnership(channelAddress, openInitiator);
    }

    private void setChannelAddressOwnership(String channelAddress, Initiator openInitiator) {
        if (openInitiator.equals(Initiator.LOCAL)) {
            addressOwnershipService.setAddressAsOwned(channelAddress);
        } else if (openInitiator.equals(Initiator.REMOTE)) {
            OwnershipStatus ownershipStatus = addressOwnershipService.getOwnershipStatus(channelAddress);
            if (!OwnershipStatus.OWNED.equals(ownershipStatus)) {
                addressOwnershipService.setAddressAsForeign(channelAddress);
            }
        }
    }

    private void setChannelAddressDescription(String channelAddress, String remotePubkey) {
        addressDescriptionService.set(channelAddress, "Lightning-Channel with " + remotePubkey);
    }
}