package emissary.server.api;

import emissary.client.EmissaryClient;
import emissary.client.response.Agent;
import emissary.client.response.AgentList;
import emissary.client.response.AgentsFormatter;
import emissary.client.response.AgentsResponseEntity;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.EmissaryNode;
import emissary.pool.MobileAgentFactory;
import emissary.server.EmissaryServer;

import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static emissary.server.api.ApiUtils.lookupPeers;
import static emissary.server.api.ApiUtils.stripPeerString;

/**
 * The agents Emissary API endpoint. Currently, contains the local (/api/agents) call and cluster (/api/clusterAgents)
 * calls.
 */
@Path("")
// context is /api and is set in EmissaryServer.java
public class Agents {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String AGENTS_ENDPOINT = "api/agents";

    @Deprecated
    public static final String AGENTS_CLUSTER_ENDPOINT = "api/cluster/agents";

    @GET
    @Path("/agents")
    @Produces(MediaType.APPLICATION_JSON)
    public Response agents() {
        return agentsV1();
    }

    @GET
    @Path("/v1/agents")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    public Response agentsV1() {
        return Response.ok().entity(lookupAgents()).build();
    }

    @GET
    @Path("/v2/agents")
    @Produces(MediaType.APPLICATION_JSON)
    public Response agentsV2() {
        return Response.ok().entity(getAgents(",", "{\"agents\":[", "]}")).build();
    }

    @GET
    @Path("/agents/log")
    @Produces(MediaType.APPLICATION_JSON)
    public Response agentsLogV2() {
        return Response.ok().entity(getAgents("\n", "", "\n")).build();
    }

    @GET
    @Path("/cluster/agents")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    public Response clusterAgents() {
        try {
            // Get our local information first
            AgentsResponseEntity entity = new AgentsResponseEntity();
            entity.setLocal(lookupAgents().getLocal());

            // Get all of our peers
            EmissaryClient client = new EmissaryClient();
            for (String peer : lookupPeers()) {
                String remoteEndPoint = stripPeerString(peer) + AGENTS_ENDPOINT;
                AgentsResponseEntity remoteEntity = client.send(new HttpGet(remoteEndPoint)).getContent(AgentsResponseEntity.class);
                entity.append(remoteEntity);
            }
            return Response.ok().entity(entity).build();
        } catch (EmissaryException e) {
            // This should never happen since we already saw if it exists
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    protected String getAgents(String delimiter, String prefix, String suffix) {
        AgentsResponseEntity entity = lookupAgents();
        AgentsFormatter formatter = AgentsFormatter.builder().withHost(entity.getLocal().getHost()).build();
        StringJoiner joiner = new StringJoiner(delimiter, prefix, suffix);
        entity.getLocal().getAgents().forEach(agent -> joiner.add("{\"agent\":" + formatter.json(agent) + "}"));
        entity.getErrors().forEach(err -> joiner.add("{\"agent\":" + formatter.json("error", err) + "}"));
        return joiner.toString();
    }

    private AgentsResponseEntity lookupAgents() {
        AgentsResponseEntity entity = new AgentsResponseEntity();
        try {
            EmissaryServer emissaryServer = (EmissaryServer) Namespace.lookup("EmissaryServer");
            EmissaryNode localNode = emissaryServer.getNode();
            String localName = localNode.getNodeName() + ":" + localNode.getNodePort();
            AgentList agents = new AgentList();
            agents.setHost(localName);
            Namespace.keySet().stream().filter(k -> k.startsWith(MobileAgentFactory.AGENT_NAME)).sorted().forEach(agentKey -> {
                try {
                    agents.addAgent(new Agent(agentKey, Namespace.lookup(agentKey).toString()));
                } catch (NamespaceException e) {
                    logger.error("Missing an agent in the Namespace: {}", agentKey);
                    entity.addError("ERROR - Agent " + agentKey + " not found in Namespace");
                }
            });
            entity.setLocal(agents);
        } catch (EmissaryException e) {
            // should never happen
            logger.error("Problem finding the emissary server or agents in the namespace, something is majorly wrong", e);
            entity.addError("Problem finding the emissary server or agents in the namespace: " + e.getMessage());
        }
        return entity;
    }
}
