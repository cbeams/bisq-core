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

package io.bisq.core.dao;

import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.bisq.common.app.AppModule;
import io.bisq.core.dao.blockchain.*;
import io.bisq.core.dao.blockchain.json.JsonBlockChainExporter;
import io.bisq.core.dao.node.BsqNodeProvider;
import io.bisq.core.dao.node.consensus.*;
import io.bisq.core.dao.node.full.FullNode;
import io.bisq.core.dao.node.full.FullNodeExecutor;
import io.bisq.core.dao.node.full.FullNodeParser;
import io.bisq.core.dao.node.full.network.FullNodeNetworkManager;
import io.bisq.core.dao.node.full.rpc.RpcService;
import io.bisq.core.dao.node.lite.LiteNode;
import io.bisq.core.dao.node.lite.LiteNodeExecutor;
import io.bisq.core.dao.node.lite.LiteNodeParser;
import io.bisq.core.dao.node.lite.network.LiteNodeNetworkManager;
import io.bisq.core.dao.request.compensation.CompensationRequestManager;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.core.dao.vote.VotingManager;
import io.bisq.core.dao.vote.VotingService;
import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

public class DaoModule extends AppModule {

    public DaoModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(DaoManager.class).in(Singleton.class);

        bind(LiteNodeNetworkManager.class).in(Singleton.class);
        bind(FullNodeNetworkManager.class).in(Singleton.class);

        bind(RpcService.class).in(Singleton.class);
        bind(FullNodeExecutor.class).in(Singleton.class);
        bind(LiteNodeExecutor.class).in(Singleton.class);
        bind(LiteNodeParser.class).in(Singleton.class);
        bind(FullNodeParser.class).in(Singleton.class);
        bind(LiteNode.class).in(Singleton.class);
        bind(FullNode.class).in(Singleton.class);
        bind(BsqNodeProvider.class).in(Singleton.class);
        bind(BsqBlockChain.class).in(Singleton.class);
        bind(ReadableBsqBlockChain.class).to(BsqBlockChain.class).in(Singleton.class);
        bind(WritableBsqBlockChain.class).to(BsqBlockChain.class).in(Singleton.class);
        bind(SnapshotManager.class).in(Singleton.class);
        bind(BsqBlockChainChangeDispatcher.class).in(Singleton.class);

        bind(GenesisTxController.class).in(Singleton.class);
        bind(BsqTxController.class).in(Singleton.class);
        bind(TxInputsController.class).in(Singleton.class);
        bind(TxInputController.class).in(Singleton.class);
        bind(TxOutputsController.class).in(Singleton.class);
        bind(TxOutputController.class).in(Singleton.class);
        bind(OpReturnController.class).in(Singleton.class);
        bind(CompensationRequestController.class).in(Singleton.class);
        bind(VotingController.class).in(Singleton.class);
        bind(IssuanceController.class).in(Singleton.class);

        bind(JsonBlockChainExporter.class).in(Singleton.class);
        bind(DaoPeriodService.class).in(Singleton.class);
        bind(VotingService.class).in(Singleton.class);

        bind(CompensationRequestManager.class).in(Singleton.class);
        bind(VotingManager.class).in(Singleton.class);
        bind(VotingDefaultValues.class).in(Singleton.class);

        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_USER)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_USER));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PASSWORD)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PASSWORD));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PORT)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT))
                .to(environment.getRequiredProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA))
                .to(environment.getRequiredProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA));
        bindConstant().annotatedWith(named(DaoOptionKeys.FULL_DAO_NODE))
                .to(environment.getRequiredProperty(DaoOptionKeys.FULL_DAO_NODE));

        String genesisTxId = environment.getProperty(DaoOptionKeys.GENESIS_TX_ID, String.class, BsqBlockChain.BTC_GENESIS_TX_ID);
        bind(String.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_TX_ID)).toInstance(genesisTxId);

        Integer genesisBlockHeight = environment.getProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT, Integer.class, BsqBlockChain.BTC_GENESIS_BLOCK_HEIGHT);
        bind(Integer.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT)).toInstance(genesisBlockHeight);
    }
}

