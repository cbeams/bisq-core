/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.proposal.compensation;

import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.proposal.Proposal;
import io.bisq.core.dao.proposal.ProposalPayload;
import io.bisq.core.dao.proposal.ProposalType;
import io.bisq.core.dao.vote.VoteResult;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Locally persisted CompensationRequest data.
 */
@Getter
public class CompensationRequest extends Proposal {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CompensationRequest(ProposalPayload proposalPayload, long fee) {
        super(proposalPayload, fee, null, false, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CompensationRequest(ProposalPayload proposalPayload,
                                long fee,
                                @Nullable VoteResult voteResult,
                                boolean closed,
                                @Nullable Map<String, String> extraDataMap) {
        super(proposalPayload,
                fee,
                voteResult,
                closed,
                extraDataMap);
    }

    @Override
    public PB.Proposal toProtoMessage() {
        return getProposalBuilder().setCompensationRequest(PB.CompensationRequest.newBuilder())
                .build();
    }

    public static CompensationRequest fromProto(PB.Proposal proto) {
        return new CompensationRequest(ProposalPayload.fromProto(proto.getProposalPayload()),
                proto.getFee(),
                proto.hasVoteResult() ? VoteResult.fromProto(proto.getVoteResult()) : null,
                proto.getClosed(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getRequestedBsq() {
        checkNotNull(getCompensationRequestPayload());
        return getCompensationRequestPayload().getRequestedBsq();
    }

    public Address getIssuanceAddress(BsqWalletService bsqWalletService) {
        checkNotNull(getCompensationRequestPayload());
        // Remove leading 'B'
        String underlyingBtcAddress = getCompensationRequestPayload().getBsqAddress().substring(1, getCompensationRequestPayload().getBsqAddress().length());
        return Address.fromBase58(bsqWalletService.getParams(), underlyingBtcAddress);
    }

    private CompensationRequestPayload getCompensationRequestPayload() {
        return (CompensationRequestPayload) proposalPayload;
    }

    @Override
    public ProposalType getType() {
        return ProposalType.COMPENSATION_REQUEST;
    }
}
