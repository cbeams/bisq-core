package io.bisq.core.dao.node.lite.network;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Log;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.util.Tuple2;
import io.bisq.core.dao.node.messages.GetBsqBlocksResponse;
import io.bisq.core.dao.node.messages.NewBsqBlockBroadcastMessage;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.*;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.seed.SeedNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsible for requesting BSQ blocks from a full node and for listening to new blocks broadcasted by full nodes.
 */
@Slf4j
public class LiteNodeNetworkManager implements MessageListener, ConnectionListener, PeerManager.Listener {

    private static final long RETRY_DELAY_SEC = 10;
    private static final long CLEANUP_TIMER = 120;
    private static final int MAX_RETRY = 3;

    private int retryCounter = 0;
    private int lastRequestedBlockHeight;
    private int lastReceivedBlockHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onNoSeedNodeAvailable();

        void onRequestedBlocksReceived(GetBsqBlocksResponse getBsqBlocksResponse);

        void onNewBlockReceived(NewBsqBlockBroadcastMessage newBsqBlockBroadcastMessage);

        void onFault(String errorMessage, @Nullable Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final Collection<NodeAddress> seedNodeAddresses;

    private final List<Listener> listeners = new ArrayList<>();

    // Key is tuple of seedNode address and requested blockHeight
    private final Map<Tuple2<NodeAddress, Integer>, RequestBlocksHandler> requestBlocksHandlerMap = new HashMap<>();
    private Timer retryTimer;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public LiteNodeNetworkManager(NetworkNode networkNode,
                                  PeerManager peerManager,
                                  SeedNodeRepository seedNodesRepository) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        // seedNodeAddresses can be empty (in case there is only 1 seed node, the seed node starting up has no other seed nodes)
        this.seedNodeAddresses = new HashSet<>(seedNodesRepository.getSeedNodeAddresses());

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);
        peerManager.addListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("Duplicates")
    public void shutDown() {
        Log.traceCall();
        stopped = true;
        stopRetryTimer();
        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
        peerManager.removeListener(this);
        closeAllHandlers();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void requestBlocks(int startBlockHeight) {
        Log.traceCall();
        lastRequestedBlockHeight = startBlockHeight;
        Optional<Connection> connectionToSeedNodeOptional = networkNode.getConfirmedConnections().stream()
                .filter(peerManager::isSeedNode)
                .findAny();
        if (connectionToSeedNodeOptional.isPresent() &&
                connectionToSeedNodeOptional.get().getPeersNodeAddressOptional().isPresent()) {
            requestBlocks(connectionToSeedNodeOptional.get().getPeersNodeAddressOptional().get(), startBlockHeight);
        } else {
            tryWithNewSeedNode(startBlockHeight);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        Log.traceCall();
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        Log.traceCall();
        closeHandler(connection);

        if (peerManager.isNodeBanned(closeConnectionReason, connection)) {
            connection.getPeersNodeAddressOptional().ifPresent(nodeAddress -> {
                seedNodeAddresses.remove(nodeAddress);
                removeFromRequestBlocksHandlerMap(nodeAddress);
            });
        }
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        Log.traceCall();
        closeAllHandlers();
        stopRetryTimer();
        stopped = true;

        tryWithNewSeedNode(lastRequestedBlockHeight);
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        Log.traceCall();
        closeAllHandlers();
        stopped = false;
        tryWithNewSeedNode(lastRequestedBlockHeight);
    }

    @Override
    public void onAwakeFromStandby() {
        log.info("onAwakeFromStandby");
        closeAllHandlers();
        stopped = false;
        if (!networkNode.getAllConnections().isEmpty())
            tryWithNewSeedNode(lastRequestedBlockHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (networkEnvelop instanceof NewBsqBlockBroadcastMessage) {
            listeners.forEach(listener -> listener.onNewBlockReceived((NewBsqBlockBroadcastMessage) networkEnvelop));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestData
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestBlocks(NodeAddress peersNodeAddress, int startBlockHeight) {
        if (!stopped) {
            final Tuple2<NodeAddress, Integer> key = new Tuple2<>(peersNodeAddress, startBlockHeight);
            if (!requestBlocksHandlerMap.containsKey(key)) {
                if (startBlockHeight >= lastReceivedBlockHeight) {
                    RequestBlocksHandler requestBlocksHandler = new RequestBlocksHandler(networkNode,
                            peerManager,
                            peersNodeAddress,
                            startBlockHeight,
                            new RequestBlocksHandler.Listener() {
                                @Override
                                public void onComplete(GetBsqBlocksResponse getBsqBlocksResponse) {
                                    log.trace("requestBlocksHandler of outbound connection complete. nodeAddress={}",
                                            peersNodeAddress);
                                    stopRetryTimer();

                                    // need to remove before listeners are notified as they cause the update call
                                    requestBlocksHandlerMap.remove(key);
                                    // we only notify if our request was latest
                                    if (startBlockHeight >= lastReceivedBlockHeight) {
                                        lastReceivedBlockHeight = startBlockHeight;
                                        listeners.forEach(listener -> listener.onRequestedBlocksReceived(getBsqBlocksResponse));
                                    } else {
                                        log.warn("We got a response which is already obsolete because we receive a " +
                                                "response from a request with a higher block height. " +
                                                "This could theoretically happen, but is very unlikely.");
                                    }
                                }

                                @Override
                                public void onFault(String errorMessage, @Nullable Connection connection) {
                                    log.warn("requestBlocksHandler with outbound connection failed.\n\tnodeAddress={}\n\t" +
                                            "ErrorMessage={}", peersNodeAddress, errorMessage);

                                    peerManager.handleConnectionFault(peersNodeAddress);
                                    requestBlocksHandlerMap.remove(key);

                                    listeners.forEach(listener -> listener.onFault(errorMessage, connection));

                                    tryWithNewSeedNode(startBlockHeight);
                                }
                            });
                    requestBlocksHandlerMap.put(key, requestBlocksHandler);
                    requestBlocksHandler.requestBlocks();
                } else {
                    //TODO check with re-orgs
                    // FIXME when a lot of blocks are created we get caught here. Seems to be a threading issue...
                    log.warn("startBlockHeight must not be smaller than lastReceivedBlockHeight. That should never happen." +
                            "startBlockHeight={},lastReceivedBlockHeight={}", startBlockHeight, lastReceivedBlockHeight);
                    if (DevEnv.isDevMode())
                        throw new RuntimeException("startBlockHeight must be larger than lastReceivedBlockHeight. startBlockHeight=" +
                                startBlockHeight + " / lastReceivedBlockHeight=" + lastReceivedBlockHeight);
                }
            } else {
                log.warn("We have started already a requestDataHandshake to peer. nodeAddress=" + peersNodeAddress + "\n" +
                        "We start a cleanup timer if the handler has not closed by itself in between 2 minutes.");

                UserThread.runAfter(() -> {
                    if (requestBlocksHandlerMap.containsKey(key)) {
                        RequestBlocksHandler handler = requestBlocksHandlerMap.get(key);
                        handler.stop();
                        requestBlocksHandlerMap.remove(key);
                    }
                }, CLEANUP_TIMER);
            }
        } else {
            log.warn("We have stopped already. We ignore that requestData call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void tryWithNewSeedNode(int startBlockHeight) {
        Log.traceCall();
        if (retryTimer == null) {
            retryCounter++;
            if (retryCounter <= MAX_RETRY) {
                retryTimer = UserThread.runAfter(() -> {
                            log.trace("retryTimer called");
                            stopped = false;

                            stopRetryTimer();

                            List<NodeAddress> list = seedNodeAddresses.stream()
                                    .filter(e -> peerManager.isSeedNode(e) && !peerManager.isSelf(e))
                                    .collect(Collectors.toList());
                            Collections.shuffle(list);

                            if (!list.isEmpty()) {
                                NodeAddress nextCandidate = list.get(0);
                                seedNodeAddresses.remove(nextCandidate);
                                log.info("We try requestBlocks with {}", nextCandidate);
                                requestBlocks(nextCandidate, startBlockHeight);
                            } else {
                                log.warn("No more seed nodes available we could try.");
                                listeners.forEach(Listener::onNoSeedNodeAvailable);
                            }
                        },
                        RETRY_DELAY_SEC);
            } else {
                log.warn("We tried {} times but could not connect to a seed node.", retryCounter);
                listeners.forEach(Listener::onNoSeedNodeAvailable);
            }
        } else {
            log.warn("We have a retry timer already running.");
        }
    }

    private void stopRetryTimer() {
        if (retryTimer != null) {
            retryTimer.stop();
            retryTimer = null;
        }
    }

    private void closeHandler(Connection connection) {
        Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
        if (peersNodeAddressOptional.isPresent()) {
            NodeAddress nodeAddress = peersNodeAddressOptional.get();
            removeFromRequestBlocksHandlerMap(nodeAddress);
        } else {
            log.trace("closeHandler: nodeAddress not set in connection " + connection);
        }
    }

    private void removeFromRequestBlocksHandlerMap(NodeAddress nodeAddress) {
        requestBlocksHandlerMap.entrySet().stream()
                .filter(e -> e.getKey().first.equals(nodeAddress))
                .findAny()
                .map(Map.Entry::getValue)
                .ifPresent(handler -> {
                    final Tuple2<NodeAddress, Integer> key = new Tuple2<>(handler.getNodeAddress(), handler.getStartBlockHeight());
                    requestBlocksHandlerMap.get(key).cancel();
                    requestBlocksHandlerMap.remove(key);
                });
    }


    private void closeAllHandlers() {
        requestBlocksHandlerMap.values().forEach(RequestBlocksHandler::cancel);
        requestBlocksHandlerMap.clear();
    }
}
