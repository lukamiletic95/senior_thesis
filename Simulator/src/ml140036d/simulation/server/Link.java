package ml140036d.simulation.server;

import ml140036d.simulation.server.Server;

public class Link {

    public enum LinkType {
        LAN,
        WAN
    }

    private LinkType linkType;
    private Server peer;

    public Link(LinkType linkType, Server peer) {
        this.linkType = linkType;
        this.peer = peer;
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public Server getPeer() {
        return peer;
    }

}
