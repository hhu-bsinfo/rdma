package de.hhu.bsinfo.neutrino.api.network.impl.util;

import de.hhu.bsinfo.neutrino.api.network.impl.InternalConnection;
import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public interface NeutrinoOutbound {
    Mono<Void> send(InternalConnection connection, Publisher<ByteBuf> data);
}
