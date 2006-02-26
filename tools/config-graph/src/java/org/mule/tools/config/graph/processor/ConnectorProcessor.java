package org.mule.tools.config.graph.processor;

import com.oy.shared.lm.graph.Graph;
import com.oy.shared.lm.graph.GraphNode;
import org.jdom.Element;
import org.mule.tools.config.graph.config.ColorRegistry;
import org.mule.tools.config.graph.config.GraphConfig;
import org.mule.tools.config.graph.util.MuleTag;

import java.util.Iterator;
import java.util.List;

public class ConnectorProcessor extends TagProcessor {

    private ConnectionStrategyProcessor connectionStrategyProcessor;

	public ConnectorProcessor(GraphConfig config) {
		super(config);
        connectionStrategyProcessor = new ConnectionStrategyProcessor(config);
	}

	public void parseConnectors(Graph graph, Element root) {
        if(!config.isShowConnectors()) return;

		List connectorsElement = root.getChildren(MuleTag.ELEMENT_CONNECTOR);
		for (Iterator iter = connectorsElement.iterator(); iter.hasNext();) {
			Element connector = (Element) iter.next();
			GraphNode connectorNode = graph.addNode();
			connectorNode.getInfo().setFillColor(ColorRegistry.COLOR_CONNECTOR);
			String name = connector.getAttributeValue(MuleTag.ATTRIBUTE_NAME);
			connectorNode.getInfo().setHeader(name);

			StringBuffer caption = new StringBuffer();

			String className = connector.getAttributeValue(MuleTag.ATTRIBUTE_CLASS_NAME);
			caption.append(MuleTag.ATTRIBUTE_CLASS_NAME + " :" + className + "\n");

			appendProfiles(connector, caption);
			appendProperties(connector, caption);
			appendDescription(connector, caption);
			connectorNode.getInfo().setCaption(caption.toString());

            //Process connection strategy
            connectionStrategyProcessor.parseConnectionStrategy(graph, connector, connectorNode);
		}
	}
}
