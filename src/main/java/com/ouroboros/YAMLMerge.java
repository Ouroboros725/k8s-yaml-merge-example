package com.ouroboros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created on 1/5/2017.
 */
public class YAMLMerge {

    public static void main(String[] args) {
        File baseFile = Paths.get(args[0]).toFile();
        File extFile = Paths.get(args[1]).toFile();

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        JsonNode baseNode;
        JsonNode extNode;
        try {
            baseNode = mapper.readTree(baseFile);
            extNode = mapper.readTree(extFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid YAML files to be merged.", e);
        }

        JsonNode resultNode = mergeJsonNode(baseNode, extNode);
        File resultFile = Paths.get(args[2]).toFile();

        try (FileOutputStream fos = new FileOutputStream(resultFile)) {
            SequenceWriter sw = mapper.writerWithDefaultPrettyPrinter().writeValues(fos);
            sw.write(resultNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save the merged YAML file.", e);
        }
    }

    private static ObjectNode merge(ObjectNode baseNode, ObjectNode extNode) {
        ObjectNode node = baseNode.deepCopy();

        for (Iterator<Map.Entry<String, JsonNode>> ite = extNode.fields(); ite.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = ite.next();
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            JsonNode child = node.get(entry.getKey());
            JsonNode result;
            if (child != null) {
                result = mergeJsonNode(child, fieldValue);
            } else {
                result = fieldValue.deepCopy();
            }

            node.set(fieldName, result);
        }

        return node;
    }

    private static ArrayNode merge(ArrayNode baseNode, ArrayNode extNode) {
        ArrayNode resultNode = baseNode.deepCopy();

        Map<String, Integer> nameMap = new HashMap<>(baseNode.size());

        int i = -1;
        for (JsonNode node : resultNode) {
            i++;

            if (JsonNodeType.OBJECT.equals(node.getNodeType())) {
                ObjectNode objNode = (ObjectNode) node;
                JsonNode nameNode = objNode.get("name");
                if (nameNode != null && JsonNodeType.STRING.equals(nameNode.getNodeType())) {
                    TextNode textNode = (TextNode) nameNode;
                    nameMap.put(textNode.textValue(), i);
                    continue;
                }
            }

            throw new IllegalArgumentException("Unsupported YAML list node to be merged.");
        }

        for (JsonNode node : extNode) {
            if (JsonNodeType.OBJECT.equals(node.getNodeType())) {
                ObjectNode objNode = (ObjectNode) node;
                JsonNode nameNode = objNode.get("name");
                if (nameNode != null && JsonNodeType.STRING.equals(nameNode.getNodeType())) {
                    TextNode textNode = (TextNode) nameNode;
                    Integer index = nameMap.get(textNode.textValue());

                    if (index != null) {
                        JsonNode result = mergeJsonNode(resultNode.get(index), objNode);
                        resultNode.set(index, result);
                    } else {
                        resultNode.add(objNode.deepCopy());
                    }

                    continue;
                }
            }

            throw new IllegalArgumentException("Unsupported YAML list node to be merged.");
        }

        return resultNode;
    }

    private static ValueNode merge(ValueNode baseNode, ValueNode extNode) {
        return extNode.deepCopy();
    }

    private static JsonNode mergeJsonNode(JsonNode baseNode, JsonNode extNode) {
        switch (baseNode.getNodeType()) {
            case OBJECT:
                return merge((ObjectNode) baseNode, (ObjectNode) extNode);
            case ARRAY:
                return merge((ArrayNode) baseNode, (ArrayNode) extNode);
            case BINARY:
            case BOOLEAN:
            case NUMBER:
            case STRING:
                return merge((ValueNode) baseNode, (ValueNode) extNode);
            default:
                throw new IllegalArgumentException("Unsupported YAML node to be merged.");
        }
    }
}
