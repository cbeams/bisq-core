/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.blockchain;

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.util.FunctionalReadWriteLock;
import io.bisq.common.util.Tuple2;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxType;
import io.bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * Mutual state of the BSQ blockchain data.
 * <p>
 * We only have one thread which is writing data from the lite node or full node executors).
 * We use ReentrantReadWriteLock in a functional style.
 * <p>
 * We limit the access to BsqBlockChain over interfaces for read (ReadableBsqBlockChain) and
 * write (WritableBsqBlockChain) to have better overview and control about access.
 */
@Slf4j
public class BsqBlockChain implements PersistableEnvelope, WritableBsqBlockChain, ReadableBsqBlockChain {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final int ISSUANCE_MATURITY = 144 * 30; // 30 days
    private static final Coin GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5");

    //mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.

    // BTC MAIN NET
    public static final String BTC_GENESIS_TX_ID = "e5c8313c4144d219b5f6b2dacf1d36f2d43a9039bb2fcd1bd57f8352a9c9809a";
    public static final int BTC_GENESIS_BLOCK_HEIGHT = 477865; // 2017-07-28


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onBlockAdded(BsqBlock bsqBlock);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final String genesisTxId;
    private final int genesisBlockHeight;

    private final LinkedList<BsqBlock> bsqBlocks;
    private final Map<String, Tx> txMap;
    private final Map<TxIdIndexTuple, TxOutput> unspentTxOutputsMap;

    // not impl in PB yet
    private final Set<Tuple2<Long, Integer>> compensationRequestFees;
    private final Set<Tuple2<Long, Integer>> votingFees;

    private final List<Listener> listeners = new ArrayList<>();

    private int chainHeadHeight = 0;
    @Nullable
    private Tx genesisTx;

    transient private final FunctionalReadWriteLock lock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqBlockChain(@Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                         @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;


        bsqBlocks = new LinkedList<>();
        txMap = new HashMap<>();
        unspentTxOutputsMap = new HashMap<>();
        compensationRequestFees = new HashSet<>();
        votingFees = new HashSet<>();

        lock = new FunctionalReadWriteLock(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqBlockChain(LinkedList<BsqBlock> bsqBlocks,
                          Map<String, Tx> txMap,
                          Map<TxIdIndexTuple, TxOutput> unspentTxOutputsMap,
                          String genesisTxId,
                          int genesisBlockHeight,
                          int chainHeadHeight,
                          @Nullable Tx genesisTx) {
        this.bsqBlocks = bsqBlocks;
        this.txMap = txMap;
        this.unspentTxOutputsMap = unspentTxOutputsMap;
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
        this.chainHeadHeight = chainHeadHeight;
        this.genesisTx = genesisTx;

        lock = new FunctionalReadWriteLock(true);

        // TODO not impl yet in PB
        compensationRequestFees = new HashSet<>();
        votingFees = new HashSet<>();
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBsqBlockChain(getBsqBlockChainBuilder()).build();
    }

    private PB.BsqBlockChain.Builder getBsqBlockChainBuilder() {
        final PB.BsqBlockChain.Builder builder = PB.BsqBlockChain.newBuilder()
                .addAllBsqBlocks(bsqBlocks.stream()
                        .map(BsqBlock::toProtoMessage)
                        .collect(Collectors.toList()))
                .putAllTxMap(txMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                v -> v.getValue().toProtoMessage())))
                .putAllUnspentTxOutputsMap(unspentTxOutputsMap.entrySet().stream()
                        .collect(Collectors.toMap(k -> k.getKey().getAsString(),
                                v -> v.getValue().toProtoMessage())))
                .setGenesisTxId(genesisTxId)
                .setGenesisBlockHeight(genesisBlockHeight)
                .setChainHeadHeight(chainHeadHeight);

        Optional.ofNullable(genesisTx).ifPresent(e -> builder.setGenesisTx(genesisTx.toProtoMessage()));

        return builder;
    }

    public static PersistableEnvelope fromProto(PB.BsqBlockChain proto) {
        return new BsqBlockChain(new LinkedList<>(proto.getBsqBlocksList().stream()
                .map(BsqBlock::fromProto)
                .collect(Collectors.toList())),
                new HashMap<>(proto.getTxMapMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, v -> Tx.fromProto(v.getValue())))),
                new HashMap<>(proto.getUnspentTxOutputsMapMap().entrySet().stream()
                        .collect(Collectors.toMap(k -> new TxIdIndexTuple(k.getKey()), v -> TxOutput.fromProto(v.getValue())))),
                proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeadHeight(),
                proto.hasGenesisTx() ? Tx.fromProto(proto.getGenesisTx()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: BsqBlockChain
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void applySnapshot(BsqBlockChain snapshot) {
        lock.write(() -> {
            bsqBlocks.clear();
            bsqBlocks.addAll(snapshot.bsqBlocks);

            txMap.clear();
            txMap.putAll(snapshot.txMap);

            unspentTxOutputsMap.clear();
            unspentTxOutputsMap.putAll(snapshot.unspentTxOutputsMap);

            chainHeadHeight = snapshot.chainHeadHeight;
            genesisTx = snapshot.genesisTx;
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addBlock(BsqBlock bsqBlock) {
        lock.write(() -> {
            bsqBlocks.add(bsqBlock);
            chainHeadHeight = bsqBlock.getHeight();
            printDetails();
            listeners.forEach(l -> l.onBlockAdded(bsqBlock));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setGenesisTx(Tx tx) {
        lock.write(() -> genesisTx = tx);
    }

    @Override
    public void addTxToMap(Tx tx) {
        lock.write(() -> txMap.put(tx.getId(), tx));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> {
            checkArgument(txOutput.isVerified(), "txOutput must be verified at addUnspentTxOutput");
            unspentTxOutputsMap.put(txOutput.getTxIdIndexTuple(), txOutput);
        });
    }

    @Override
    public void removeUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> unspentTxOutputsMap.remove(txOutput.getTxIdIndexTuple()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: Fees
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setCreateCompensationRequestFee(long fee, int blockHeight) {
        lock.write(() -> compensationRequestFees.add(new Tuple2<>(fee, blockHeight)));
    }

    @Override
    public void setVotingFee(long fee, int blockHeight) {
        lock.write(() -> votingFees.add(new Tuple2<>(fee, blockHeight)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: BsqBlockChain
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public BsqBlockChain getClone() {
        return lock.read(() -> getClone(this));
    }

    @Override
    public BsqBlockChain getClone(BsqBlockChain bsqBlockChain) {
        return lock.read(() -> (BsqBlockChain) BsqBlockChain.fromProto(bsqBlockChain.getBsqBlockChainBuilder().build()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public LinkedList<BsqBlock> getBsqBlocks() {
        return lock.read(() -> bsqBlocks);
    }

    @Override
    public boolean containsBsqBlock(BsqBlock bsqBlock) {
        return lock.read(() -> bsqBlocks.contains(bsqBlock));
    }

    @Override
    public int getChainHeadHeight() {
        return chainHeadHeight;
    }

    @Override
    public int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    @Override
    public List<BsqBlock> getClonedBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            BsqBlockChain clone = getClone();
            return clone.bsqBlocks.stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .peek(bsqBlock -> bsqBlock.reset())
                    .collect(Collectors.toList());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Optional<Tx> getOptionalTx(String txId) {
        return lock.read(() -> Optional.ofNullable(txMap.get(txId)));
    }

    @Override
    public Map<String, Tx> getTxMap() {
        return lock.read(() -> txMap);
    }

    @Override
    public Set<Tx> getTransactions() {
        return lock.read(() -> getTxMap().entrySet().stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet()));
    }

    @Override
    public Set<Tx> getFeeTransactions() {
        return lock.read(() -> getTxMap().entrySet().stream()
                .filter(e -> e.getValue().getBurntFee() > 0)
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet()));
    }

    @Override
    public boolean hasTxBurntFee(String txId) {
        return lock.read(() -> getOptionalTx(txId)
                .map(Tx::getBurntFee)
                .filter(fee -> fee > 0)
                .isPresent());
    }

    @Override
    public boolean containsTx(String txId) {
        return lock.read(() -> getOptionalTx(txId).isPresent());
    }

    @Nullable
    public Tx getGenesisTx() {
        return genesisTx;
    }

    @Override
    public String getGenesisTxId() {
        return genesisTxId;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isTxOutputSpendable(String txId, int index) {
        return lock.read(() -> getSpendableTxOutput(txId, index).isPresent());
    }

    @Override
    public Set<TxOutput> getUnspentTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().filter(e -> e.isVerified() && e.isUnspent()).collect(Collectors.toSet()));
    }

    @Override
    public Set<TxOutput> getSpentTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().filter(e -> e.isVerified() && !e.isUnspent()).collect(Collectors.toSet()));
    }

    @Override
    public Optional<TxOutput> getSpendableTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> getUnspentTxOutput(txIdIndexTuple)
                .filter(this::isTxOutputMature));
    }

    @Override
    public Optional<TxOutput> getSpendableTxOutput(String txId, int index) {
        return lock.read(() -> getSpendableTxOutput(new TxIdIndexTuple(txId, index)));
    }

    private Optional<TxOutput> getUnspentTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> unspentTxOutputsMap.entrySet().stream()
                .filter(e -> e.getKey().equals(txIdIndexTuple))
                .map(Map.Entry::getValue).findAny());
    }

    private Set<TxOutput> getAllTxOutputs() {
        return txMap.values().stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .collect(Collectors.toSet());
    }

    //TODO
    // for genesis we don't need it and for issuance we need more implemented first
    private boolean isTxOutputMature(TxOutput spendingTxOutput) {
        return lock.read(() -> true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: TxType
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Optional<TxType> getTxType(String txId) {
        return lock.read(() -> getOptionalTx(txId).map(Tx::getTxType));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Coin getTotalBurntFee() {
        return lock.read(() -> Coin.valueOf(getTxMap().entrySet().stream().mapToLong(e -> e.getValue().getBurntFee()).sum()));
    }

    @Override
    public Coin getIssuedAmount() {
        return lock.read(() -> BsqBlockChain.GENESIS_TOTAL_SUPPLY);
    }

    @Override
    public long getCreateCompensationRequestFee(int blockHeight) {
        return lock.read(() -> {
            long fee = -1;
            for (Tuple2<Long, Integer> feeAtHeight : compensationRequestFees) {
                if (feeAtHeight.second <= blockHeight)
                    fee = feeAtHeight.first;
            }
            checkArgument(fee > -1, "compensationRequestFees must be set");
            return fee;
        });
    }

    //TODO not impl yet
    @Override
    public boolean isCompensationRequestPeriodValid(int blockHeight) {
        return lock.read(() -> true);

    }

    long getVotingFee(int blockHeight) {
        return lock.read(() -> {
            long fee = -1;
            for (Tuple2<Long, Integer> feeAtHeight : votingFees) {
                if (feeAtHeight.second <= blockHeight)
                    fee = feeAtHeight.first;
            }
            checkArgument(fee > -1, "votingFee must be set");
            return fee;
        });
    }

    //TODO not impl yet
    boolean isVotingPeriodValid(int blockHeight) {
        return lock.read(() -> true);
    }

    boolean existsCompensationRequestBtcAddress(String btcAddress) {
        return lock.read(() -> getAllTxOutputs().stream()
                .anyMatch(txOutput -> txOutput.isCompensationRequestBtcOutput() &&
                        btcAddress.equals(txOutput.getAddress())));
    }

    @Override
    public void printDetails() {
        log.debug("\nchainHeadHeight={}\n" +
                        "    blocks.size={}\n" +
                        "    txMap.size={}\n" +
                        "    unspentTxOutputsMap.size={}\n" +
                        "    compensationRequestFees.size={}\n" +
                        "    votingFees.size={}\n" +
                        getChainHeadHeight(),
                bsqBlocks.size(),
                txMap.size(),
                unspentTxOutputsMap.size(),
                compensationRequestFees.size(),
                votingFees.size());
    }


    // Probably not needed anymore
    public <T> T callFunctionWithWriteLock(Supplier<T> supplier) {
        return lock.write(supplier);
    }

}

