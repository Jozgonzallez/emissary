package emissary.command;

import emissary.client.EmissaryClient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static emissary.Emissary.setupLogbackForConsole;

@Parameters(commandDescription = "Output the configured values for certain properties")
public class EnvCommand extends HttpCommand {

    static final Logger LOG = LoggerFactory.getLogger(EnvCommand.class);

    public static int DEFAULT_PORT = 8001;

    @Parameter(names = {"--bashable"}, description = "format output for sourcing by bash")
    private boolean bashable = false;

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public String getCommandName() {
        return "env";
    }

    public boolean getBashable() {
        return bashable;
    }

    @Override
    public boolean getQuiet() {
        // if bashable, make it quiet too
        if (bashable) {
            return true;
        }
        return super.getQuiet();
    }

    @Override
    public void run(JCommander jc) {
        String endpoint = getScheme() + "://" + getHost() + ":" + getPort() + "/api/env";

        if (getBashable()) {
            setup();
            LoggerContext lc = setupLogbackForConsole();

            // still gotta hide org.eclipse.jetty.util.log INFO
            ch.qos.logback.classic.Logger jettyUtilLogger = lc.getLogger("org.eclipse.jetty.util.log");
            jettyUtilLogger.setLevel(Level.WARN);

            // also add .sh to the endpoint
            endpoint = endpoint + ".sh";
            LOG.info("# generated from env command at {}", endpoint);
        } else {
            setup(); // go ahead an log it
        }
        EmissaryClient client = new EmissaryClient();
        LOG.info(client.send(new HttpGet(endpoint)).getContentString());
    }

    @Override
    public void setupCommand() {
        setupEnv();
    }

    public void setupEnv() {
        setupConfig();
    }

    @Override
    public void outputBanner() {
        // override so we can use the getQuiet defined here
        if (isVerbose()) {
            new Banner().dump();
        }
    }


}
