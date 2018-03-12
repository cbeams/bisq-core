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

package io.bisq.core.dao.proposal.generic;

import io.bisq.common.app.Version;
import io.bisq.core.dao.proposal.ProposalPayload;
import io.bisq.core.dao.proposal.ProposalType;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;

/**
 * Payload for generic proposals.
 */
@Slf4j
@Data
public final class GenericProposalPayload extends ProposalPayload {

    public GenericProposalPayload(String uid,
                                  String name,
                                  String title,
                                  String description,
                                  String link,
                                  NodeAddress nodeAddress,
                                  PublicKey ownerPubKey,
                                  Date creationDate) {
        super(uid,
                name,
                title,
                description,
                link,
                nodeAddress.getFullAddress(),
                Utils.HEX.encode(ownerPubKey.getEncoded()),
                Version.COMPENSATION_REQUEST_VERSION,
                creationDate.getTime(),
                null,
                null,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GenericProposalPayload(String uid,
                                   String name,
                                   String title,
                                   String description,
                                   String link,
                                   String nodeAddress,
                                   String ownerPubPubKeyAsHex,
                                   byte version,
                                   long creationDate,
                                   String signature,
                                   String txId,
                                   @Nullable Map<String, String> extraDataMap) {
        super(uid,
                name,
                title,
                description,
                link,
                nodeAddress,
                ownerPubPubKeyAsHex,
                version,
                creationDate,
                signature,
                txId,
                extraDataMap);
    }

    private PB.ProposalPayload.Builder getGenericProposalPayloadBuilder() {
        final PB.GenericProposalPayload.Builder compensationRequestPayloadBuilder = PB.GenericProposalPayload.newBuilder();
        return getProposalPayloadBuilder().setGenericProposalPayload(compensationRequestPayloadBuilder);
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        return PB.StoragePayload.newBuilder().setProposalPayload(getGenericProposalPayloadBuilder()).build();
    }

    public static GenericProposalPayload fromProto(PB.ProposalPayload proto) {
        return new GenericProposalPayload(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                proto.getNodeAddress(),
                proto.getOwnerPubKeyAsHex(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getSignature(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }

    @Override
    public ProposalType getType() {
        return ProposalType.COMPENSATION_REQUEST;
    }

}
