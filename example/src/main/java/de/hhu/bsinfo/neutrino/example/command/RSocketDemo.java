package de.hhu.bsinfo.neutrino.example.command;

import de.hhu.bsinfo.neutrino.api.Neutrino;
import de.hhu.bsinfo.neutrino.api.connection.Connection;
import de.hhu.bsinfo.neutrino.api.connection.ConnectionService;
import de.hhu.bsinfo.neutrino.api.core.CoreService;
import de.hhu.bsinfo.neutrino.api.util.QueuePairAddress;
import io.rsocket.*;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.neutrino.client.InfinibandClientTransport;
import io.rsocket.transport.neutrino.server.InfinibandServerTransport;
import io.rsocket.util.DefaultPayload;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import picocli.CommandLine;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Slf4j
@CommandLine.Command(
        name = "rsocket",
        description = "Demonstrates rsocket using neutrino as a transport.%n",
        showDefaultValues = true,
        separator = " ")
public class RSocketDemo implements Runnable {

    private static final byte PORT_NUMBER = 1;
    private static final int DEFAULT_SERVER_PORT = 2998;
    private static final Duration SEND_INTERVAL = Duration.ofMillis(200);

    @CommandLine.Option(
            names = "--server",
            description = "Runs this instance in server mode.")
    private boolean isServer;

    @CommandLine.Option(
            names = {"-c", "--connect"},
            description = "The server to connect to.")
    private InetSocketAddress serverAddress;

    @CommandLine.Option(
            names = {"-p", "--port"},
            description = "The port the server will listen on.")
    private int port = DEFAULT_SERVER_PORT;

    private static final QueuePairAddress DUMMY_ADDRESS = QueuePairAddress.builder().build();

    private final Neutrino neutrino = Neutrino.newInstance();
    private final CoreService coreService = neutrino.getService(CoreService.class);
    private final ConnectionService connectionService = neutrino.getService(ConnectionService.class);

    @Override
    public void run() {
        if (isServer) {
            startServer();
        } else {
            startClient();
        }
    }

    private void startClient() {
        // TODO(krakowski): Implement connection via URI string
        //  var clientTransport = UriTransportRegistry.clientForUri(connectionString);

        log.info("Connecting to {}", serverAddress);
        try (var socket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {
            var connection = connectionService.newConnection().block();
            var remoteAddress = connect(socket, connection);
            var transport = InfinibandClientTransport.create(connection, remoteAddress, neutrino);
            var rsocket = RSocketFactory.connect()
                    .frameDecoder(PayloadDecoder.ZERO_COPY)
                    .acceptor(rSocket -> new DemoSocket())
                    .transport(transport)
                    .start()
                    .block();

            log.info("Starting stream");
            Flux.interval(SEND_INTERVAL)
                .map(second -> DefaultPayload.create("Hello Infiniworld! (" + second + ")"))
                .compose(rsocket::requestChannel)
                .doOnNext(payload -> log.info("Received echo message \"{}\"", payload.getDataUtf8()))
                .blockLast();
        } catch (IOException e) {
            log.error("An unexpected error occured", e);
        }
    }

    private void startServer() {
        log.info("Waiting for incoming connections on port {}", port);
        try (var serverSocket = new ServerSocket(port);
             var socket = serverSocket.accept()) {

            var connection = connectionService.newConnection().block();
            var remoteAddress = connect(socket, connection);
            var transport = InfinibandServerTransport.create(connection, remoteAddress, neutrino);
            var rsocket = RSocketFactory.receive()
                    .frameDecoder(PayloadDecoder.ZERO_COPY)
                    .acceptor((setup, reactiveSocket) -> Mono.just(new DemoSocket()))
                    .transport(transport)
                    .start()
                    .block();

            rsocket.onClose().block();
        } catch (IOException e) {
            log.error("An unexpected error occured", e);
        }
    }

    private QueuePairAddress connect(Socket socket, Connection connection) {
        var queuePair = connection.getQueuePair();
        var localInfo = QueuePairAddress.builder()
                .localId(coreService.getLocalId())
                .queuePairNumber(queuePair.getQueuePairNumber())
                .portNumber(PORT_NUMBER)
                .build();

        try (var out = new ObjectOutputStream(socket.getOutputStream());
             var in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(localInfo);
            return (QueuePairAddress) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    private static final class DemoSocket extends AbstractRSocket {

        @Override
        public Mono<Void> fireAndForget(Payload payload) {
            var message = payload.getDataUtf8();
            log.info("[fireAndForget] received message \"{}\"", message);
            return Mono.empty();
        }

        @Override
        public Mono<Payload> requestResponse(Payload payload) {
            var message = payload.getDataUtf8();
            log.info("[requestResponse] received message \"{}\"", message);
            return Mono.just(DefaultPayload.create(message));
        }

        @Override
        public Flux<Payload> requestStream(Payload payload) {
            var message = payload.getDataUtf8();
            log.info("[requestStream] received message \"{}\"", message);
            return Flux.interval(Duration.ofSeconds(1))
                    .map(it -> DefaultPayload.create(message));
        }

        @Override
        public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
            return Flux.from(payloads)
                    .map(Payload::getDataUtf8)
                    .doOnNext(message -> log.info("[requestChannel] received message \"{}\"", message))
                    .map(DefaultPayload::create);
        }
    }
}
