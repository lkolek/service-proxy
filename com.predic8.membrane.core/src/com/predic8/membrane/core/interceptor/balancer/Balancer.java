package com.predic8.membrane.core.interceptor.balancer;

import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.GenericComplexElement;

public class Balancer extends AbstractXmlElement {
	public static final String DEFAULT_NAME = "Default";
	private static Log log = LogFactory.getLog(Balancer.class.getName());

	private final Map<String, Cluster> clusters = new Hashtable<String, Cluster>();
	private String name = DEFAULT_NAME;
	private long timeout = 0;
	private SessionCleanupThread sct;

	public Balancer() {
		addCluster(Cluster.DEFAULT_NAME);
		sct = new SessionCleanupThread(clusters);
		sct.start();
	}
	
	@Override
	protected void finalize() throws Throwable {
		if (sct != null) {
			sct.interrupt();
			sct = null;
		}
		super.finalize();
	}
	
	public long getSessionTimeout() {
		return sct == null ? 0 : sct.getSessionTimeout();
	}
	
	public void setSessionTimeout(long sessionTimeout) {
		if (sessionTimeout == 0) {
			if (sct != null) {
				sct.interrupt();
				sct = null;
			}
		} else {
			if (sct == null) {
				sct = new SessionCleanupThread(clusters);
				sct.start();
			}
			sct.setSessionTimeout(sessionTimeout);
		}
	}
	

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public Collection<Cluster> getClusters() {
		return clusters.values();
	}
	
	private Cluster getCluster(String name) {
		if (!clusters.containsKey(name)) // backward-compatibility: auto create clusters as they are accessed
			addCluster(name);
		return clusters.get(name);
	}

	public boolean addCluster(String name) {
		if (clusters.containsKey(name))
			return false;
		log.debug("adding cluster with name [" + name + "] to balancer [" + name + "]");
		clusters.put(name, new Cluster(name));
		return true;
	}

	public void up(String cName, String host, int port) {
		getCluster(cName).nodeUp(new Node(host, port));
	}

	public void down(String cName, String host, int port) {
		getCluster(cName).nodeDown(new Node(host, port));
	}

	public void takeout(String cName, String host, int port) {
		getCluster(cName).nodeTakeOut(new Node(host, port));
	}

	public List<Node> getAllNodesByCluster(String cName) {
		return getCluster(cName).getAllNodes(timeout);
	}

	public List<Node> getAvailableNodesByCluster(String cName) {
		return getCluster(cName).getAvailableNodes(timeout);
	}

	public void addSession2Cluster(String sessionId, String cName, Node n) {
		getCluster(cName).addSession(sessionId, n);
	}

	public void removeNode(String cluster, String host, int port) {
		getCluster(cluster).removeNode(new Node(host, port));
	}

	public Node getNode(String cluster, String host, int port) {
		return getCluster(cluster).getNode(new Node(host, port));
	}

	public Map<String, Session> getSessions(String cluster) {
		return getCluster(cluster).getSessions();
	}

	public List<Session> getSessionsByNode(String cName, Node node) {
		return getCluster(cName).getSessionsByNode(node);
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (token.getLocalName().equals("cluster")) {
			final GenericComplexElement c = new GenericComplexElement();
			c.setChildParser(new AbstractXmlElement() {
				@Override
				protected void parseChildren(XMLStreamReader token, String child)
						throws Exception {
					if (token.getLocalName().equals("node")) {
						GenericComplexElement n = new GenericComplexElement();
						n.parse(token);
						up(c.getAttribute("name"), n.getAttribute("host"),
								Integer.parseInt(n.getAttribute("port")));
					} else {
						super.parseChildren(token, child);
					}
				}
			});
			c.parse(token);
		} else {
			super.parseChildren(token, child);
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("clusters");
		for (Cluster c : clusters.values()) {
			out.writeStartElement("cluster");
			out.writeAttribute("name", c.getName());

			for (Node n : c.getAllNodes(0)) {
				out.writeStartElement("node");
				out.writeAttribute("host", n.getHost());
				out.writeAttribute("port", "" + n.getPort());
				out.writeEndElement();
			}
			out.writeEndElement();
		}
		out.writeEndElement();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
}
