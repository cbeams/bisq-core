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

package bisq.cli;

import bisq.daemon.BalanceRequest;
import bisq.daemon.WalletGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

@Command(
    name = "bisq",
    version = "0.1.0",
    mixinStandardHelpOptions = true,

    subcommands = {
        HelpCommand.class,
        BisqBalance.class,
        BisqAccount.class
    }
)
class Bisq implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}

@Command(
    name = "balance"
)
class BisqBalance implements Runnable {

    @Override
    public void run() {
        ManagedChannel server = ManagedChannelBuilder.forAddress("localhost", 50051)
            .usePlaintext(true)
            .build();
        WalletGrpc.WalletBlockingStub wallet = WalletGrpc.newBlockingStub(server);
        System.out.println(Double.valueOf(wallet.getBalance(BalanceRequest.getDefaultInstance()).getValue()));
    }
}

@Command(
    name = "account"
)
class BisqAccount implements Runnable {

    @Override
    public void run() {
        System.out.println("account-A");
        System.out.println("account-B");
        System.out.println("account-C");
    }
}
