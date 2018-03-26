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

package bisq.daemon;

import bisq.daemon.BisqDaemonServer.GreeterImpl;

import io.grpc.testing.GrpcServerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class BisqDaemonServerTest {
    /**
     * This creates and starts an in-process server, and creates a client with an in-process channel.
     * When the test is done, it also shuts down the in-process client and server.
     */
    @Rule
    public final GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    /**
     * To test the server, make calls with a real stub using the in-process channel, and verify
     * behaviors or state changes from the client side.
     */
    @Test
    public void greeterImpl_replyMessage() {
        // Add the service to the in-process server.
        grpcServerRule.getServiceRegistry().addService(new GreeterImpl());

        GreeterGrpc.GreeterBlockingStub blockingStub =
            GreeterGrpc.newBlockingStub(grpcServerRule.getChannel());
        String testName = "test name";

        HelloReply reply = blockingStub.sayHello(HelloRequest.newBuilder().setName(testName).build());

        assertEquals("Hello " + testName, reply.getMessage());
    }
}
