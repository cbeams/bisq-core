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

import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;



import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;

@RunWith(JUnit4.class)
public class BisqDaemonClientTest {
    /**
     * This creates and starts an in-process server, and creates a client with an in-process channel.
     * When the test is done, it also shuts down the in-process client and server.
     */
    @Rule
    public final GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    private final GreeterGrpc.GreeterImplBase serviceImpl =
        mock(GreeterGrpc.GreeterImplBase.class, delegatesTo(new GreeterGrpc.GreeterImplBase() {
        }));
    private BisqDaemonClient client;

    @Before
    public void setUp() throws Exception {
        // Add service.
        grpcServerRule.getServiceRegistry().addService(serviceImpl);
        // Create a HelloWorldClient using the in-process channel;
        client = new BisqDaemonClient(grpcServerRule.getChannel());
    }

    /**
     * To test the client, call from the client against the fake server, and verify behaviors or state
     * changes from the server side.
     */
    @Test
    public void greet_messageDeliveredToServer() {
        ArgumentCaptor<HelloRequest> requestCaptor = ArgumentCaptor.forClass(HelloRequest.class);
        String testName = "test name";

        client.greet(testName);

        verify(serviceImpl)
            .sayHello(requestCaptor.capture(), Matchers.<StreamObserver<HelloReply>>any());
        assertEquals(testName, requestCaptor.getValue().getName());
    }
}
