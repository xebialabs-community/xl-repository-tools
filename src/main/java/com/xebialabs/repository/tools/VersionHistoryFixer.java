package com.xebialabs.repository.tools;


import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import javax.jcr.Credentials;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.VersionHistory;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.version.InconsistentVersioningState;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionHistoryFixer {

    @Option(name = "-xlHome", usage = "The path to your XL product home directory", required = true)
    private String xlHomePath;

    @Option(name = "-password", usage = "Password of the admin user", required = false)
    private String adminPassword = "admin";

    public static void main(String[] args) throws Exception {
        new VersionHistoryFixer().doMain(args);
    }

    public void doMain(String[] args) throws Exception {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);

        File xlHome = new File(xlHomePath);
        File confDir = new File(xlHome, "conf");
        File confFile = new File(confDir, "jackrabbit-repository.xml");
        // Assumption: the repository directory is in the product home directory. This value _should_ be read from conf/deployit.conf or conf/xl-release-server.conf
        File repositoryDir = new File(xlHome, "repository");
        RepositoryConfig repositoryConfig = RepositoryConfig.create(confFile, repositoryDir);
        RepositoryImpl repository = RepositoryImpl.create(repositoryConfig);
        Credentials creds = new SimpleCredentials("admin", adminPassword.toCharArray());
        Session session = repository.login(creds);
        try {
            Node rootNode = session.getNode("/");
            NodeIterator allNodes = rootNode.getNodes();
            AtomicLong currentNode = new AtomicLong();
            processNodes(session, allNodes, currentNode);
            logger.info("Done!");
        } finally {
            session.logout();
        }
    }

    private void processNodes(Session session, NodeIterator allNodes, AtomicLong currentNode) throws RepositoryException {
        while (allNodes.hasNext()) {
            if((currentNode.incrementAndGet() % 100) == 0) {
                logger.info("Processing node #" + currentNode);
            }
            Node n = allNodes.nextNode();
            cleanVersionHistory(session, n);
            if(n.getPath().equals("/jcr:system")) {
                continue;
            }
            processNodes(session, n.getNodes(), currentNode);
        }
    }

    private void cleanVersionHistory(Session session, Node n) throws RepositoryException {
        // System.out.println("ID = " + n.getIdentifier() + ", path = " + n.getPath());
        if (n.isNodeType(NodeType.MIX_VERSIONABLE)) {
            try {
                final VersionHistory history = session.getWorkspace().getVersionManager().getVersionHistory(n.getPath());
            } catch (InconsistentVersioningState exc) {
                logger.info("Fixing broken version history for node " + n.getIdentifier() + " with path " + n.getPath());
                n.removeMixin(NodeType.MIX_VERSIONABLE);
                session.save();
                n.addMixin(NodeType.MIX_VERSIONABLE);
                session.save();
            }
        }
    }

    private Logger logger = LoggerFactory.getLogger(getClass());

}
