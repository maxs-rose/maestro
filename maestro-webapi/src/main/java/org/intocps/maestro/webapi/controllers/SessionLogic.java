package org.intocps.maestro.webapi.controllers;

import org.intocps.maestro.webapi.maestro2.dto.InitializationData;
import org.springframework.web.socket.WebSocketSession;

import java.io.File;

public class SessionLogic {
    public final File rootDirectory;
    private InitializationData initializationData;
    private WebSocketSession socket;

    public SessionLogic(File rootDirectory) {
        rootDirectory.mkdir();
        this.rootDirectory = rootDirectory;

    }


    public WebSocketSession getSocket() {
        return socket;
    }

    public InitializationData getInitializationData() {
        return initializationData;
    }

    public void setInitializationData(InitializationData initializationData) {
        this.initializationData = initializationData;
    }


    public void setWebsocketSession(WebSocketSession socket) {
        this.socket = socket;
    }

    public Boolean containsSocket() {
        if (this.socket == null) {
            return false;
        } else {
            if (this.socket.isOpen()) {
                return true;
            } else {
                this.socket = null;
                return false;
            }
        }
    }

    public void removeSocket() {
        this.socket = null;
    }
}
