package com.xebialabs.repository.tools;


import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.ConfigurationException;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.config.WorkspaceConfig;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.commons.value.QValueValue;
import org.apache.jackrabbit.value.ReferenceValue;
import org.apache.jackrabbit.value.WeakReferenceValue;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import java.io.File;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

public class NodeForNodeRepositoryCopier {

    private static Logger logger = LoggerFactory.getLogger(NodeForNodeRepositoryCopier.class);

    @Option(name = "-srcHome", usage = "The path to your source XL product home directory", required = true)
    private String srcHome;

    @Option(name = "-dstHome", usage = "The path to your target XL product home directory", required = true)
    private String dstHome;

    @Option(name = "-dstPassword", usage = "The password to your target XL product home directory", required = true)
    private String dstPassword;

    public static void main(String[] args) throws Exception {
        new NodeForNodeRepositoryCopier().doMain(args);
    }

    public void doMain(String[] args) throws Exception {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);

        RepositoryConfig srcRepositoryConfig = getRepositoryConfig(srcHome);
        disableIndexes(srcRepositoryConfig);

        RepositoryConfig dstRepositoryConfig = getRepositoryConfig(dstHome);

        // Connect
        Session srcSession = getSession(srcRepositoryConfig, new GuestCredentials());
        try {
            Session dstSession = getSession(dstRepositoryConfig, new SimpleCredentials("admin", dstPassword.toCharArray()));
            try {
                AtomicLong currentNodeCounter = new AtomicLong();
                processNodes(srcSession.getRootNode(), dstSession.getRootNode(), currentNodeCounter);

                logger.info("Saving session");
                dstSession.save();

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

    private Session getSession(RepositoryConfig repositoryConfig, Credentials credentials) throws RepositoryException {
        RepositoryImpl repository = RepositoryImpl.create(repositoryConfig);
        return repository.login(credentials);
    }

    private void processNodes(Node srcParentNode, Node dstParentNode, AtomicLong currentNodeCounter) throws RepositoryException {
        NodeIterator allNodes = srcParentNode.getNodes();
        while (allNodes.hasNext()) {
            if ((currentNodeCounter.incrementAndGet() % 100) == 0) {
                if(currentNodeCounter.get() != 0) {
                    logger.info("Saving session");
                    dstParentNode.getSession().save();
                }

                logger.info("Processing node #" + currentNodeCounter);
            }
            Node node = allNodes.nextNode();
            if (node.getPath().equals("/jcr:system")) {
                continue;
            }
            Node dstNode = copyNode(dstParentNode, node);
            processNodes(node, dstNode, currentNodeCounter);
        }
    }

    private Node copyNode(Node dstParentNode, Node srcNode) throws RepositoryException {
        Node dstNode;
        if (dstParentNode.hasNode(srcNode.getName())) {
            dstNode = dstParentNode.getNode(srcNode.getName());
        } else {
            dstNode = dstParentNode.addNode(srcNode.getName());
        }

        logger.debug("Copying node {} to node {} for path {}", srcNode.getIdentifier(), dstNode.getIdentifier(), srcNode.getPath());

        dstNode.setPrimaryType(srcNode.getPrimaryNodeType().getName());
        for (NodeType type : srcNode.getMixinNodeTypes()) {
            dstNode.addMixin(type.getName());
        }

        PropertyIterator propertyIterator = srcNode.getProperties();
        while (propertyIterator.hasNext()) {
            Property property = propertyIterator.nextProperty();
            if (property.getName().startsWith("jcr:"))
                continue;

            if(property.getType() == PropertyType.REFERENCE || property.getType() == PropertyType.WEAKREFERENCE) {
                logger.trace("Skipping reference property {}", property.getName());
                continue;
            }

            if (property.isMultiple()) {
                Value[] srcValues = property.getValues();
                logger.trace("Copying property {} with value {}", property.getName(), srcValues);
                if(srcValues.length > 0 && isReferenceValue(srcValues[0])) {
                    continue;
                }
                dstNode.setProperty(property.getName(), srcValues);
            } else {
                Value srcValue = property.getValue();
                logger.trace("Copying property {} with value {}", property.getName(), srcValue);
                if(isReferenceValue(srcValue)) {
                    continue;
                }
                dstNode.setProperty(property.getName(), srcValue);
            }
        }

        return dstNode;
    }

    private boolean isReferenceValue(Value val) {
        if(val instanceof QValueValue) {
            int t = ((QValueValue) val).getQValue().getType();
            return t == PropertyType.REFERENCE || t == PropertyType.WEAKREFERENCE;
        } else {
            return val instanceof ReferenceValue || val instanceof WeakReferenceValue;
        }
    }
}
