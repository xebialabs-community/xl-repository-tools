package com.xebialabs.repository.tools;


import java.io.File;
import javax.jcr.Credentials;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.VersionHistory;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.version.InconsistentVersioningState;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.jcr.query.Query.JCR_SQL2;

public class VersionHistoryCleaner {

    @Option(name = "-xlHome", usage = "The path to your XL product home directory", required = true)
    private String xlHomePath;

    @Option(name = "-password", usage = "Password of the admin user", required = false)
    private String adminPassword = "admin";

    public static void main(String[] args) throws Exception {
        new VersionHistoryCleaner().doMain(args);
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
//            QueryManager qm = session.getWorkspace().getQueryManager();
//            System.out.println("Querying all nodes");
//            Query query = qm.createQuery("select * from [nt:base]", JCR_SQL2);
//            QueryResult qr = query.execute();
//            NodeIterator allNodes = qr.getNodes();
            Node rootNode = session.getNode("/");
            NodeIterator allNodes = rootNode.getNodes();
            long totalNrOfNodes = allNodes.getSize();
            System.out.println("# of nodes = " + totalNrOfNodes);
            long currentNode = 0;
            while (allNodes.hasNext()) {
                currentNode++;
                if((currentNode % 100) == 0) {
                    System.out.println("Processing node " + currentNode + " of " + totalNrOfNodes);
                }
                Node n = allNodes.nextNode();
                System.out.println("ID = " + n.getIdentifier() + ", path = " + n.getPath());
                if (n.isNodeType(NodeType.MIX_VERSIONABLE)) {
                    try {
                        final VersionHistory history = session.getWorkspace().getVersionManager().getVersionHistory(n.getPath());
                    } catch (InconsistentVersioningState exc) {
                        System.out.println("Fixing broken version history for node " + n.getIdentifier() + " with path " + n.getPath());
                        n.removeMixin(NodeType.MIX_VERSIONABLE);
                        session.save();
                        n.addMixin(NodeType.MIX_VERSIONABLE);
                        session.save();
                    }
                }
            }
            System.out.println("Done!");
        } finally {
            session.logout();
        }
    }

    private Logger logger = LoggerFactory.getLogger(getClass());

}
