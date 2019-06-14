package de.hhu.bsinfo.neutrino.api.message.impl;

import de.hhu.bsinfo.neutrino.api.message.MessageService;
import de.hhu.bsinfo.neutrino.api.util.NullOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageServiceImpl extends MessageService<NullOptions> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageServiceImpl.class);

    @Override
    public void testMethod() {
        LOGGER.info("testMethod called");
    }

    @Override
    protected void onInit() {

    }

    @Override
    protected void onShutdown() {

    }
}
