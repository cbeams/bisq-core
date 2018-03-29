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

package bisq.core.dao.node.consensus;

import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.consensus.OpReturnType;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies if a given transaction is a BSQ OP_RETURN transaction.
 */
@Slf4j
public class OpReturnController {
    private final OpReturnCompReqController opReturnCompReqController;
    private final OpReturnBlindVoteController opReturnBlindVoteController;
    private final OpReturnVoteRevealController opReturnVoteRevealController;

    @Inject
    public OpReturnController(OpReturnCompReqController opReturnCompReqController,
                              OpReturnBlindVoteController opReturnBlindVoteController,
                              OpReturnVoteRevealController opReturnVoteRevealController) {
        this.opReturnCompReqController = opReturnCompReqController;
        this.opReturnBlindVoteController = opReturnBlindVoteController;
        this.opReturnVoteRevealController = opReturnVoteRevealController;
    }

    // We only check partially the rules here as we do not know the BSQ fee at that moment which is always used when
    // we have OP_RETURN data.
    public void processOpReturnCandidate(TxOutput txOutput, Model model) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (txOutput.getValue() == 0 && opReturnData != null && opReturnData.length >= 1) {
            final OpReturnType opReturnType = OpReturnType.getOpReturnType(opReturnData[0]);
            if (opReturnType != null)
                model.setOpReturnTypeCandidate(opReturnType);
        }
    }

    public void process(TxOutput txOutput, Tx tx, int index, long bsqFee, int blockHeight, Model model) {
        final long txOutputValue = txOutput.getValue();
        // A BSQ OP_RETURN has to be the last output, the txOutputValue has to be 0 as well as there have to be a BSQ fee.
        if (txOutputValue == 0 && index == tx.getOutputs().size() - 1 && bsqFee > 0) {
            byte[] opReturnData = txOutput.getOpReturnData();
            if (opReturnData != null) {
                // All BSQ OP_RETURN txs have at least a type byte
                if (opReturnData.length >= 1) {
                    // Check with the type byte which kind of OP_RETURN we have.
                    OpReturnType opReturnType = OpReturnType.getOpReturnType(opReturnData[0]);
                    if (opReturnType != null) {
                        switch (opReturnType) {
                            case COMPENSATION_REQUEST:
                                if (opReturnCompReqController.verify(opReturnData, bsqFee, blockHeight, model)) {
                                    opReturnCompReqController.applyStateChange(txOutput, model);
                                } else {
                                    log.warn("We expected a compensation request op_return data but it did not " +
                                            "match our rules. tx={}", tx);
                                }
                                break;
                            case PROPOSAL:
                                // TODO
                                break;
                            case BLIND_VOTE:
                                if (opReturnBlindVoteController.verify(opReturnData, bsqFee, blockHeight, model)) {
                                    opReturnBlindVoteController.applyStateChange(txOutput, model);
                                } else {
                                    log.warn("We expected a blind vote op_return data but it did not " +
                                            "match our rules. tx={}", tx);
                                }
                                break;
                            case VOTE_REVEAL:
                                if (opReturnVoteRevealController.verify(opReturnData, bsqFee, blockHeight, model)) {
                                    opReturnVoteRevealController.applyStateChange(txOutput, model);
                                } else {
                                    log.warn("We expected a vote reveal op_return data but it did not " +
                                            "match our rules. tx={}", tx);
                                }
                                break;
                            case LOCK_UP:
                                // TODO
                                break;
                            case UNLOCK:
                                // TODO
                                break;
                            default:
                                // Should never happen as getOpReturnType() would return null
                                final String msg = "OP_RETURN does not match expected version bytes. tx=" + tx.getId();
                                log.error(msg);
                                if (DevEnv.isDevMode())
                                    throw new RuntimeException(msg);

                                break;
                        }
                    } else {
                        log.warn("OP_RETURN version of the BSQ tx ={} does not match expected version bytes. opReturnData={}",
                                tx.getId(), Utils.HEX.encode(opReturnData));
                    }
                } else {
                    log.warn("opReturnData is null or has no content. opReturnData={}", Utils.HEX.encode(opReturnData));
                }
            } else {
                // TODO check if that can be a valid state or if we should thrown an exception
                log.warn("opReturnData is null");
            }
        } else {
            log.warn("opReturnData is not matching DAO rules txId={} outValue={} index={} #outputs={} bsqFee={}",
                    tx.getId(), txOutputValue, index, tx.getOutputs().size(), bsqFee);
        }
    }
}
