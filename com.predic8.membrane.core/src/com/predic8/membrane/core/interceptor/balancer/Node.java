package com.predic8.membrane.core.interceptor.balancer;

import java.net.MalformedURLException;
import java.util.*;

import javax.xml.stream.*;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.StatisticCollector;

public class Node extends AbstractXmlElement {

	public static enum Status {
		UP, DOWN, TAKEOUT;
	}
	
	private long lastUpTime;
	private String host;
	private int port;
	private Status status;
	private int counter;
	private int threads;
	
	private Map<Integer, StatisticCollector> statusCodes = new Hashtable<Integer, StatisticCollector>();  
	
	public Node(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public Node() {
	}

	@Override
	public boolean equals(Object obj) {
		return obj!=null && obj instanceof Node &&
			   host.equals(((Node)obj).getHost()) &&
			   port == ((Node)obj).getPort();
	}
	
	public synchronized int getLost() {
		int received = 0;
		for ( StatisticCollector statisticCollector : statusCodes.values() ) {
			received += statisticCollector.getCount();
		}			
		return counter - received - threads;
	}

	public synchronized double getErrors() {
		int successes = 0;
		int all = 0;
		for (Map.Entry<Integer, StatisticCollector> e: statusCodes.entrySet() ) {
			all += e.getValue().getCount();
			if ( e.getKey() < 500 && e.getKey() > 0) {
				successes += e.getValue().getCount();
			}
		}			
		return all == 0 ? 0: 1-(double)successes/all; 
	}
	
	public long getLastUpTime() {
		return lastUpTime;
	}

	public void setLastUpTime(long lastUpTime) {
		this.lastUpTime = lastUpTime;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isUp() {
		return status == Status.UP;
	}

	public boolean isDown() {
		return status == Status.DOWN;		
	}
	
	public boolean isTakeOut() {
		return status == Status.TAKEOUT;		
	}
	
	public void setStatus(Status status) {
		if (status==Status.DOWN) threads = 0;
		this.status = status;
	}
	
	public Status getStatus() {
		return status;
	}

	@Override
	public String toString() {
		return "["+host+":"+port+"]";
	}

	public synchronized int getCounter() {
		return counter;
	}

	public synchronized void incCounter() {
		counter++;		
	}

	public synchronized void clearCounter() {
		counter = 0;	
		statusCodes.clear();
	}

	public synchronized void collectStatisticsFrom(Exchange exc) {
		int code = exc.getResponse().getStatusCode();
		if ( !statusCodes.containsKey(code) ) {
			statusCodes.put(code, new StatisticCollector(true));
		}
		statusCodes.get(code).collectFrom(exc);			
	}
	
	public synchronized void addThread() {
		if (!isUp()) return;
		++threads;		
	}

	public synchronized void removeThread() {
		if (!isUp()) return;
		--threads;		
	}

	public synchronized int getThreads() {
		return threads;
	}

	public Map<Integer, StatisticCollector> getStatisticsByStatusCodes() {
		return statusCodes;
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("node");

		out.writeAttribute("host", host);
		out.writeAttribute("port", ""+port);

		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		
		host = token.getAttributeValue("", "host");
		port = Integer.parseInt(token.getAttributeValue("", "port")!=null?token.getAttributeValue("", "port"):"80");
	}
	
	public String getDestinationURL(Exchange exc)
			throws MalformedURLException {
		return "http://" + getHost() + ":" + getPort() + exc.getRequestURI();
	}

}
