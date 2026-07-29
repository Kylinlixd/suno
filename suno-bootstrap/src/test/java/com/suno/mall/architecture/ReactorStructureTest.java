package com.suno.mall.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ReactorStructureTest {

    @Test
    void rootPomIsAnOrderedModularReactor() throws Exception {
        File rootPom = new File("../pom.xml");
        var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Files.newInputStream(rootPom.toPath()));
        Element project = document.getDocumentElement();
        NodeList moduleNodes = project.getElementsByTagName("module");
        var modules = java.util.stream.IntStream.range(0, moduleNodes.getLength())
                .mapToObj(moduleNodes::item)
                .map(node -> node.getTextContent().trim())
                .toList();

        NodeList packagingNodes = project.getElementsByTagName("packaging");
        String packaging = packagingNodes.getLength() == 0
                ? "jar"
                : packagingNodes.item(0).getTextContent().trim();

        assertEquals("pom", packaging);
        assertEquals(List.of(
                "suno-core",
                "suno-identity",
                "suno-recycle",
                "suno-marketplace",
                "suno-payment",
                "suno-operations",
                "suno-test-support",
                "suno-bootstrap"), modules);
    }
}
