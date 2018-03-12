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

package io.bisq.core.dao.proposal;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.util.JsonExclude;
import io.bisq.core.dao.proposal.compensation.CompensationRequestPayload;
import io.bisq.core.dao.proposal.generic.GenericProposalPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import io.bisq.network.p2p.storage.payload.LazyProcessedPayload;
import io.bisq.network.p2p.storage.payload.PersistableProtectedPayload;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Payload sent over wire as well as it gets persisted, containing all base data for a compensation request
 */
//TODO requestedBsq and bsqAddress are only relevant for comp.Request. Use ProposalPayload as base class for
// non comp.Request proposal and create subclass for comp.Request with requestedBsq and bsqAddress
@Slf4j
@Data
public abstract class ProposalPayload implements LazyProcessedPayload, PersistableProtectedPayload, CapabilityRequiringPayload {
    protected final String uid;
    protected final String name;
    protected final String title;
    protected final String description;
    protected final String link;
    protected final String nodeAddress;
    // used for json
    protected String ownerPubPubKeyAsHex;
    // Signature of the JSON data of this object excluding the signature and feeTxId fields using the standard Bitcoin
    // messaging signing format as a base64 encoded string.
    @JsonExclude
    protected String signature;
    // Set after we signed and set the hash. The hash is used in the OP_RETURN of the fee tx
    @JsonExclude
    protected String txId;

    protected final byte version;
    protected final long creationDate;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    protected Map<String, String> extraDataMap;

    // Used just for caching
    @JsonExclude
    @Nullable
    protected transient PublicKey ownerPubKey;

    public ProposalPayload(String uid,
                           String name,
                           String title,
                           String description,
                           String link,
                           NodeAddress nodeAddress,
                           PublicKey ownerPubKey,
                           Date creationDate) {
        this(uid,
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

    protected ProposalPayload(String uid,
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
        this.uid = uid;
        this.name = name;
        this.title = title;
        this.description = description;
        this.link = link;
        this.nodeAddress = nodeAddress;
        this.ownerPubPubKeyAsHex = ownerPubPubKeyAsHex;
        this.version = version;
        this.creationDate = creationDate;
        this.signature = signature;
        this.txId = txId;
        this.extraDataMap = extraDataMap;
    }

    public PB.ProposalPayload.Builder getProposalPayloadBuilder() {
        final PB.ProposalPayload.Builder builder = PB.ProposalPayload.newBuilder()
                .setUid(uid)
                .setName(name)
                .setTitle(title)
                .setDescription(description)
                .setLink(link)
                .setNodeAddress(nodeAddress)
                .setOwnerPubKeyAsHex(ownerPubPubKeyAsHex)
                .setVersion(version)
                .setCreationDate(creationDate)
                .setSignature(signature)
                .setTxId(txId);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        return PB.StoragePayload.newBuilder().setProposalPayload(getProposalPayloadBuilder()).build();
    }

    //TODO add other proposal types
    public static ProposalPayload fromProto(PB.ProposalPayload proto) {
        switch (proto.getMessageCase()) {
            case COMPENSATION_REQUEST_PAYLOAD:
                return CompensationRequestPayload.fromProto(proto);
            case GENERIC_PROPOSAL_PAYLOAD:
                return GenericProposalPayload.fromProto(proto);
            default:
                throw new ProtobufferException("Unknown message case: " + proto.getMessageCase());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO not needed?
    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        if (ownerPubKey == null)
            ownerPubKey = Sig.getPublicKeyFromBytes(Utils.HEX.decode(ownerPubPubKeyAsHex));
        return ownerPubKey;
    }

    // Pre 0.6 version don't know the new message type and throw an error which leads to disconnecting the peer.
    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.COMP_REQUEST.ordinal()
        ));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getCreationDate() {
        return new Date(creationDate);
    }

    public NodeAddress getNodeAddress() {
        return new NodeAddress(nodeAddress);
    }

    public String getShortId() {
        return uid.substring(0, 8);
    }

    public abstract ProposalType getType();
}
