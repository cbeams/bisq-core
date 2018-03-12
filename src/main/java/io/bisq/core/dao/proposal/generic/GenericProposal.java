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

package io.bisq.core.dao.proposal.generic;

import io.bisq.core.dao.proposal.Proposal;
import io.bisq.core.dao.proposal.ProposalPayload;
import io.bisq.core.dao.proposal.ProposalType;
import io.bisq.core.dao.vote.VoteResult;
import io.bisq.generated.protobuffer.PB;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Generic proposal for anything not covered by specific proposals.
 */
public class GenericProposal extends Proposal {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GenericProposal(ProposalPayload payload, long fee) {
        super(payload, fee, null, false, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GenericProposal(ProposalPayload proposalPayload,
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
        return getProposalBuilder().setGenericProposal(PB.GenericProposal.newBuilder())
                .build();
    }

    public static GenericProposal fromProto(PB.Proposal proto) {
        return new GenericProposal(ProposalPayload.fromProto(proto.getProposalPayload()),
                proto.getFee(),
                proto.hasVoteResult() ? VoteResult.fromProto(proto.getVoteResult()) : null,
                proto.getClosed(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.GENERIC;
    }
}
