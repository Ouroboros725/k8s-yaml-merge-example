package com.ouroboros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Created on 1/10/2017.
 */
public class DevYAML {

    public static void main(String[] args) {
        File deploymentFile = Paths.get(args[1] + "/deployment.yaml").toFile();
        File serviceFile = Paths.get(args[1] + "/service.yaml").toFile();
        File configFile = Paths.get(args[1] + "/config.yaml").toFile();

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        JsonNode deploymentNode;
        JsonNode serviceNode;
        JsonNode configNode;
        try {
            deploymentNode = mapper.readTree(deploymentFile);
            serviceNode = mapper.readTree(serviceFile);
            configNode = mapper.readTree(configFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid YAML.", e);
        }

        setDebugPort(configNode, serviceNode);
        setLogVolume(deploymentNode, args[0]);

        File tgtDeploymentFile = Paths.get(args[2] + "/deployment.yaml").toFile();
        File tgtServiceFile = Paths.get(args[2] + "/service.yaml").toFile();
        File tgtConfigFile = Paths.get(args[2] + "/config.yaml").toFile();

        try (FileOutputStream deploymentFOS = new FileOutputStream(tgtDeploymentFile)) {
            SequenceWriter sw = mapper.writerWithDefaultPrettyPrinter().writeValues(deploymentFOS);
            sw.write(deploymentNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save YAML.", e);
        }

        try (FileOutputStream serviceFOS = new FileOutputStream(tgtServiceFile)) {
            SequenceWriter sw = mapper.writerWithDefaultPrettyPrinter().writeValues(serviceFOS);
            sw.write(serviceNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save YAML.", e);
        }

        try (FileOutputStream configFOS = new FileOutputStream(tgtConfigFile)) {
            SequenceWriter sw = mapper.writerWithDefaultPrettyPrinter().writeValues(configFOS);
            sw.write(configNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save YAML.", e);
        }
    }

    private static void setDebugPort(JsonNode configNode, JsonNode serviceNode) {
        {
            JsonNode dataNode = configNode.get("data");
            if (dataNode == null || !JsonNodeType.OBJECT.equals(dataNode.getNodeType())) {
                throw new IllegalArgumentException("Invalid ConfigMap YAML.");
            }

            ObjectNode dataObjNode = (ObjectNode) dataNode;
            JsonNode javaOptNode = dataObjNode.get("java.options");
            if (javaOptNode == null || !JsonNodeType.STRING.equals(javaOptNode.getNodeType())) {
                throw new IllegalArgumentException("Invalid ConfigMap YAML.");
            }

            dataObjNode.set("java.options", JsonNodeFactory.instance.textNode(
                    javaOptNode.textValue() + " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000"));
        }

        {
            JsonNode specNode = serviceNode.get("spec");
            if (specNode == null || !JsonNodeType.OBJECT.equals(specNode.getNodeType())) {
                throw new IllegalArgumentException("Invalid Service YAML.");
            }

            ObjectNode specObjNode = (ObjectNode) specNode;

            {
                JsonNode portstNode = specObjNode.get("ports");
                if (portstNode == null || !JsonNodeType.ARRAY.equals(portstNode.getNodeType())) {
                    throw new IllegalArgumentException("Invalid Service YAML.");
                }

                ObjectNode debugPortNode = JsonNodeFactory.instance.objectNode();
                debugPortNode.set("name", JsonNodeFactory.instance.textNode("debug-port"));
                debugPortNode.set("port", JsonNodeFactory.instance.numberNode(8000));
                debugPortNode.set("targetPort", JsonNodeFactory.instance.numberNode(8000));

                ArrayNode portsAryNode = (ArrayNode) portstNode;
                portsAryNode.add(debugPortNode);
            }

            {
                specObjNode.set("type", JsonNodeFactory.instance.textNode("NodePort"));
            }
        }
    }

    private static void setLogVolume(JsonNode deploymentNode, String containerName) {
        JsonNode specNode = deploymentNode.get("spec");
        if (specNode == null || !JsonNodeType.OBJECT.equals(specNode.getNodeType())) {
            throw new IllegalArgumentException("Invalid Deployment YAML.");
        }

        JsonNode tplNode = specNode.get("template");
        if (tplNode == null || !JsonNodeType.OBJECT.equals(tplNode.getNodeType())) {
            throw new IllegalArgumentException("Invalid Deployment YAML.");
        }

        JsonNode specSubNode = tplNode.get("spec");
        if (specSubNode == null || !JsonNodeType.OBJECT.equals(specSubNode.getNodeType())) {
            throw new IllegalArgumentException("Invalid Deployment YAML.");
        }

        ObjectNode specSubObjNode = (ObjectNode) specSubNode;

        {
            JsonNode containerNode = specSubObjNode.get("containers");
            if (containerNode == null || !JsonNodeType.ARRAY.equals(containerNode.getNodeType())) {
                throw new IllegalArgumentException("Invalid Deployment YAML.");
            }

            ArrayNode containerAryNode = (ArrayNode) containerNode;
            boolean found = false;
            for (JsonNode node : containerAryNode) {
                if (JsonNodeType.OBJECT.equals(node.getNodeType())) {
                    ObjectNode objNode = (ObjectNode) node;
                    JsonNode nameNode = objNode.get("name");
                    if (nameNode == null || !JsonNodeType.STRING.equals(nameNode.getNodeType())) {
                        throw new IllegalArgumentException("Invalid Deployment YAML.");
                    }

                    if (containerName.equals(nameNode.textValue())) {
                        ObjectNode volMntObjNode = JsonNodeFactory.instance.objectNode();
                        volMntObjNode.set("name", JsonNodeFactory.instance.textNode("service-log-volume"));
                        volMntObjNode.set("mountPath", JsonNodeFactory.instance.textNode("/usr/local/log"));

                        ArrayNode volMntAryNode = JsonNodeFactory.instance.arrayNode();
                        volMntAryNode.add(volMntObjNode);

                        objNode.set("volumeMounts", volMntAryNode);

                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                throw new IllegalArgumentException("Invalid Deployment YAML.");
            }
        }

        {
            ObjectNode pathObjNode = JsonNodeFactory.instance.objectNode();
            pathObjNode.set("path", JsonNodeFactory.instance.textNode("/c/Users/logs"));

            ObjectNode volumeObjNode = JsonNodeFactory.instance.objectNode();
            volumeObjNode.set("name", JsonNodeFactory.instance.textNode("service-log-volume"));
            volumeObjNode.set("hostPath", pathObjNode);

            ArrayNode volumeAryNode = JsonNodeFactory.instance.arrayNode();
            volumeAryNode.add(volumeObjNode);

            specSubObjNode.set("volumes", volumeAryNode);
        }
    }
}
