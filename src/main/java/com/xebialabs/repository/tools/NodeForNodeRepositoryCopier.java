package com.xebialabs.repository.tools;


import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.ConfigurationException;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.config.WorkspaceConfig;
import org.apache.jackrabbit.value.ReferenceValue;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import java.io.File;
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

    private Stats stats = new Stats();

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
        RepositoryImpl srcRepository = getRepository(srcRepositoryConfig);
        Session srcSession = getSession(new GuestCredentials(), srcRepository);
        RepositoryImpl dstRepository = getRepository(dstRepositoryConfig);
        try {
            Session dstSession = getSession(new SimpleCredentials("admin", dstPassword.toCharArray()), dstRepository);
            try {
                logger.info("-------->>>>> Creating nodes <<<<----------------");
                createDstNodes(srcSession.getRootNode(), dstSession.getRootNode());
                logger.info("Saving session");
                dstSession.save();
                logger.info("Done!");
                logger.info("-------->>>>> Copying properties <<<<----------------");
                copyPropertiesToAllNodes(srcSession.getRootNode(), dstSession.getRootNode());
                dstSession.save();
                logger.info("Done!");
            } finally {
                dstSession.logout();
            }
        } finally {
            srcSession.logout();
            srcRepository.shutdown();
            dstRepository.shutdown();
            logger.info("---------->>>>> STATS <<<<<-----------");
            logger.info(stats.toString());
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

    private Session getSession(Credentials credentials, RepositoryImpl repository) throws RepositoryException {
        return repository.login(credentials);
    }

    private RepositoryImpl getRepository(RepositoryConfig repositoryConfig) throws RepositoryException {
        return RepositoryImpl.create(repositoryConfig);
    }

    private void createDstNodes(Node srcParentNode, Node dstParentNode) throws RepositoryException {
        forAllChildNodesDoAndSave(srcParentNode, dstParentNode.getSession(), (node) -> {
            Node dstNode = findOrCreateDstNode(dstParentNode, node);
            createDstNodes(node, dstNode);
        });
    }

    private Node findOrCreateDstNode(Node dstParentNode, Node srcNode) throws RepositoryException {
        Node dstNode;
        if (dstParentNode.hasNode(srcNode.getName())) {
            logger.info("Node {} already exists", srcNode.getPath());
            dstNode = dstParentNode.getNode(srcNode.getName());
        } else {
            dstNode = dstParentNode.addNode(srcNode.getName());
        }
        stats.incrementAllNodes();
        logger.debug("Creating with source id {} to destination id {} for path {}", srcNode.getIdentifier(), dstNode.getIdentifier(), srcNode.getPath());
        addMixinsAndType(srcNode, dstNode);
        return dstNode;
    }

    private void addMixinsAndType(Node srcNode, Node dstNode) throws RepositoryException {
        dstNode.setPrimaryType(srcNode.getPrimaryNodeType().getName());
        for (NodeType type : srcNode.getMixinNodeTypes()) {
            dstNode.addMixin(type.getName());
        }
    }

    private void copyPropertiesToAllNodes(Node srcParentNode, Node dstParentNode) throws RepositoryException {
        forAllChildNodesDoAndSave(srcParentNode, dstParentNode.getSession(), node -> {
            Node dstNode = dstParentNode.getNode(node.getName());
            copyPropertiesToNode(node, dstNode);
            copyPropertiesToAllNodes(node, dstNode);
        });
    }

    private void copyPropertiesToNode(Node node, Node dstNode) throws RepositoryException {
        PropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            Property property = properties.nextProperty();
            copyPropertyValue(dstNode, node, property);
        }
    }

    private void copyPropertyValue(Node dstNode, Node srcNode, Property property) throws RepositoryException {
        try {
            if (property.getName().startsWith("jcr:")){
                return;
            }
            logger.trace("Copying property {} on node {}", property.getName(), srcNode.getPath());
            if(isReference(property)) {
                copyReferenceProperty(srcNode, dstNode, property);
            } else if (property.isMultiple()) {
                Value[] srcValues = property.getValues();
                dstNode.setProperty(property.getName(), srcValues);
            } else if(property.getType() == PropertyType.BINARY) {
                Binary binary = property.getBinary();
                Binary dstBinary = dstNode.getSession().getValueFactory().createBinary(binary.getStream());
                dstNode.setProperty(property.getName(), dstBinary);
            } else {
                Value srcValue = property.getValue();
                dstNode.setProperty(property.getName(), srcValue);
            }
        } catch (RepositoryException e) {
            stats.incrementFailedProperties();
            logger.error("Could not copy property {} on id {} and path {}", property.getName(), dstNode.getIdentifier(), dstNode.getPath());
        }
    }

    private void copyReferenceProperty(Node srcNode, Node dstNode, Property property) throws RepositoryException {
        if(isReference(property)) {
            logger.trace("Setting reference property {} from node", property.getName(), srcNode.getIdentifier());
            if (property.isMultiple()) {
                Value[] srcValues = property.getValues();
                Value[] dstValues = new Value[srcValues.length];
                for (int i = 0; i < srcValues.length; i++) {
                    dstValues[i] = convertReferenceValue(srcValues[i], srcNode, dstNode);
                }
                dstNode.setProperty(property.getName(), dstValues);
            } else {
                Value srcValue = property.getValue();
                Value dstValue = convertReferenceValue(srcValue, srcNode, dstNode);
                dstNode.setProperty(property.getName(), dstValue);
            }
        }
    }

    private Value convertReferenceValue(Value srcValue, Node srcNode, Node dstNode) throws RepositoryException {
        String path = srcNode.getSession().getNodeByIdentifier(srcValue.getString()).getPath();
        return new ReferenceValue(dstNode.getSession().getNode(path));
    }

    private boolean isReference(Property property) throws RepositoryException {
        return property.getType() == PropertyType.REFERENCE;
    }

    private void forAllChildNodesDoAndSave(Node srcParentNode, Session session, NodeConsumer consumer) throws RepositoryException {
        NodeIterator allNodes = srcParentNode.getNodes();
        while (allNodes.hasNext()) {
            if (this.stats.commitSession()) {
                logger.info("Saving session on operation number {}", stats.getCounter());
                session.save();
            }
            Node node = allNodes.nextNode();
            if (node.getPath().equals("/jcr:system")) {
                continue;
            }

            consumer.consume(node);
        }
    }

    interface NodeConsumer {
        void consume(Node srcNode) throws RepositoryException;
    }
}
