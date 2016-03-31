package com.xebialabs.repository.tools;


import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.ConfigurationException;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.config.WorkspaceConfig;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

public class NodeForNodeRepositoryCopier {

    private static Logger logger = LoggerFactory.getLogger(NodeForNodeRepositoryCopier.class);

    @Option(name = "-srcHome", usage = "The path to your source XL product home directory", required = true)
    private String srcHome;

    @Option(name = "-dstHome", usage = "The path to your target XL product home directory", required = true)
    private String dstHome;

    public static void main(String[] args) throws Exception {
        new NodeForNodeRepositoryCopier().doMain(args);
    }

    public void doMain(String[] args) throws Exception {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);

        RepositoryConfig srcRepositoryConfig = getRepositoryConfig(srcHome);
        disableIndexes(srcRepositoryConfig);

        RepositoryConfig dstRepositoryconfig = getRepositoryConfig(dstHome);

        // Connect
        Session srcSession = getSession(srcRepositoryConfig);
        try {
            Session dstSession = getSession(dstRepositoryconfig);
            try {
                AtomicLong currentNodeCounter = new AtomicLong();
                processNodes(srcSession, srcSession.getRootNode(), dstSession, dstSession.getRootNode(), currentNodeCounter);
                logger.info("Done!");
            } finally {
                dstSession.logout();
            }
        } finally {
            srcSession.logout();
        }
    }

    private RepositoryConfig getRepositoryConfig(String home) throws ConfigurationException {
        File homeFile = new File(home);
        File confDir = new File(homeFile, "conf");
        File confFile = new File(confDir, "jackrabbit-repository.xml");
        // Assumption: the repository directory is in the product home directory. This value _should_ be read from conf/deployit.conf or conf/xl-release-server.conf
        File repositoryDir = new File(homeFile, "repository");
        return RepositoryConfig.create(confFile, repositoryDir);
    }

    private void disableIndexes(RepositoryConfig srcRepositoryConfig) throws NoSuchFieldException, IllegalAccessException {
        Field qhfOnRepositoryConfigField = srcRepositoryConfig.getClass().getDeclaredField("qhf");
        qhfOnRepositoryConfigField.setAccessible(true);
        qhfOnRepositoryConfigField.set(srcRepositoryConfig, null);

        for (WorkspaceConfig workspaceConfig : srcRepositoryConfig.getWorkspaceConfigs()) {
            Field qhfOnWorkspaceConfigField = workspaceConfig.getClass().getDeclaredField("qhf");
            qhfOnWorkspaceConfigField.setAccessible(true);
            qhfOnWorkspaceConfigField.set(workspaceConfig, null);
        }
    }

    private Session getSession(RepositoryConfig repositoryConfig) throws RepositoryException {
        RepositoryImpl repository = RepositoryImpl.create(repositoryConfig);
        return repository.login();
    }

    private void processNodes(Session srcSession, Node srcNode, Session dstSession, Node dstNode, AtomicLong currentNodeCounter) throws RepositoryException {
        NodeIterator allNodes = srcNode.getNodes();
        while (allNodes.hasNext()) {
            if((currentNodeCounter.incrementAndGet() % 100) == 0) {
                logger.info("Processing node #" + currentNodeCounter);
            }
            Node n = allNodes.nextNode();
            copyNode(srcSession, dstSession, dstNode, n);
            if(n.getPath().equals("/jcr:system")) {
                continue;
            }
            processNodes(srcSession, dstSession, n.getNodes(), currentNode);
        }
    }

    private void copyNode(Session srcSession, Session dstSession, Node dstParentNode, Node srcNode) throws RepositoryException {
        logger.info("Copying node {} with path {}", srcNode.getIdentifier(), srcNode.getPath());
        Node dstNode = dstParentNode.addNode(srcNode.getName());
        srcNode.get

    }

}
